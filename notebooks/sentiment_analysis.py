"""
Sentiment Analysis Pipeline - Analyzes crawled Reddit content sentiment.

Implements a dual-engine approach:
  1. VADER (NLTK) - fast rule-based lexicon method, excellent for social media text
  2. Transformers DistilBERT - contextual model for nuanced sentiment scoring
Outputs: per-post sentiment scores, subreddit-level aggregations, trend visualizations.

Design: standalone CLI + importable module interface.
Outputs JSON/CSV/Markdown reports per subreddit.

Author: DS Analyst Agent (Zarrouk6969)
Date:   2026-06-04
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import re
import sys
import time
from collections import defaultdict, Counter
from dataclasses import dataclass, field
from datetime import datetime
from io import StringIO
from typing import Any

import nltk
import numpy as np
import pandas as pd
from nltk.sentiment.vader import SentimentIntensityAnalyzer

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    stream=sys.stdout,
)
logger = logging.getLogger("sentiment_analysis")


# ---------------------------------------------------------------------------
# Data Classes
# ---------------------------------------------------------------------------

@dataclass
class SentimentScore:
    """Represents sentiment for a single post/thread."""

    post_id: str
    subreddit: str
    text_snippet: str  # first 200 chars for reference
    vader_compound: float = 0.0
    vader_pos: float = 0.0
    vader_neu: float = 0.0
    vader_neg: float = 0.0
    hf_score: float = -1.0  # HuggingFace model output; -1 means not run yet
    hf_label: str = "neutral"
    overall_label: str = ""
    confidence: float = 0.0
    analysis_method: str = ""


# ---------------------------------------------------------------------------
# Preprocessing
# ---------------------------------------------------------------------------

class TextCleaner:
    """Cleans Reddit text for consistent sentiment analysis."""

    @classmethod
    def clean(cls, text: str) -> str:
        """Remove URLs and extra whitespace; preserve punctuation for VADER."""
        if not text or not isinstance(text, str):
            return ""
        cleaned_text = re.sub(r"http\S+|www\.\S+", " URL ", text)
        cleaned_text = re.sub(r"\s+", " ", cleaned_text).strip()
        return cleaned_text

    @classmethod
    def clean_batch(cls, texts: list[str]) -> list[str]:
        return [cls.clean(t) for t in texts]


# ---------------------------------------------------------------------------
# VADER Sentiment Engine
# ---------------------------------------------------------------------------

class VaderAnalyzer:
    """NLTK's VADER sentiment analyzer - tuned for social media text."""

    _initialized = False
    _analyzer: SentimentIntensityAnalyzer | None = None

    @classmethod
    def initialize(cls) -> None:
        if cls._initialized or cls._analyzer is not None:
            return
        logger.info("Initializing VADER sentiment engine ...")
        nltk.data.find("sentiment/vader_lexicon.zip") or \
            nltk.download("vader_lexicon", quiet=True)
        cls._analyzer = SentimentIntensityAnalyzer()
        cls._initialized = True

    @classmethod
    def analyze(cls, text: str) -> dict[str, float]:
        """Return compound / pos / neu / neg scores."""
        cls.initialize()
        if cls._analyzer is None:
            raise RuntimeError(
                "VADER not initialized - call VaderAnalyzer.initialize() first"
            )
        return cls._analyzer.polarity_scores(text)

    @staticmethod
    def label_from_compound(compound: float) -> str:
        """Map VADER compound (-1..+1) to sentiment label."""
        if compound >= 0.05:
            return "positive"
        elif compound <= -0.05:
            return "negative"
        return "neutral"


# ---------------------------------------------------------------------------
# HuggingFace Transformers Sentiment Engine
# ---------------------------------------------------------------------------

class TransformersSentimentAnalyzer:
    """Contextual sentiment via a DistilBERT pipeline from HuggingFace."""

    _initialized = False
    _pipeline: Any | None = None
    DEFAULT_MODEL = "distilbert-base-uncased-finetuned-sst-2-english"
    LABEL_MAP: dict[str, float] = {"POSITIVE": 1.0, "NEGATIVE": 0.0}

    @classmethod
    def initialize(cls, model_name: str | None = None) -> None:
        if cls._initialized:
            return
        model = model_name or cls.DEFAULT_MODEL
        logger.info("Loading HuggingFace sentiment model: %s ...", model)
        try:
            from transformers import pipeline as hf_pipeline
            cls._pipeline = hf_pipeline(
                "sentiment-analysis",
                model=model,
                tokenizer=model,
                device=-1,  # CPU - upgrade to 0 for GPU when available
                framework="pt",
            )
            cls._initialized = True
            logger.info("HuggingFace sentiment model loaded successfully.")
        except ImportError:
            logger.warning(
                "transformers.pipeline not available - HF engine disabled."
            )

    @classmethod
    def analyze(cls, text: str) -> dict[str, Any]:
        """Return {'label', 'score'} from the pipeline.

        If not initialized, returns neutral with score 0.5.
        """
        if cls._pipeline is None:
            return {"label": "neutral", "score": 0.5}
        result = cls._pipeline(text)[0]
        score = cls.LABEL_MAP.get(result["label"], 0.5)
        return {
            "label": result["label"].lower(),
            "score": round(score, 4),
        }

    @classmethod
    def analyze_batch(
        cls, texts: list[str], batch_size: int = 32
    ) -> list[dict[str, Any]]:
        if cls._pipeline is None or not texts:
            return [{"label": "neutral", "score": 0.5}] * len(texts)
        results: list[dict[str, Any]] = []
        for i in range(0, len(texts), batch_size):
            batch = texts[i : i + batch_size]
            batch_results = cls._pipeline(batch)
            for r in batch_results:
                score = cls.LABEL_MAP.get(r["label"], 0.5)
                results.append({
                    "label": r["label"].lower(),
                    "score": round(score, 4),
                })
        return results


# ---------------------------------------------------------------------------
# Combined Sentiment Engine
# ---------------------------------------------------------------------------

class CombinedSentimentAnalyzer:
    """Runs both VADER and HF and produces a consensus sentiment score."""

    @staticmethod
    def analyze_post(
        post_id: str,
        subreddit: str,
        text: str,
        title: str = "",
    ) -> dict[str, Any]:
        """Return a dict with full dual-engine analysis for one post."""
        # Combine title + body; truncate to 512 chars max
        analyze_text = f"{title} {text}".strip()[:512] if (title and text) else (title or text)

        # VADER pass
        vader_scores = VaderAnalyzer.analyze(analyze_text)
        vader_label = VaderAnalyzer.label_from_compound(vader_scores["compound"])

        # HuggingFace pass
        hf_result = TransformersSentimentAnalyzer.analyze(analyze_text)
        hf_score = hf_result["score"]
        hf_label = hf_result.get("label", "neutral")

        # Consensus: weighted average  (0.4 VADER + 0.6 HF)
        vader_numeric = 0.5 + 0.5 * vader_scores["compound"]  # -1..1 -> 0..1
        combined_score = 0.4 * vader_numeric + 0.6 * hf_score

        if combined_score >= 0.6:
            overall_label = "positive"
        elif combined_score <= 0.4:
            overall_label = "negative"
        else:
            overall_label = "neutral"

        # Confidence based on inter-model agreement
        agree_count = sum([
            vader_label == "positive" and hf_label == "positive",
            vader_label == "negative" and hf_label == "negative",
            vader_label == "neutral" and abs(vader_scores["compound"]) < 0.05,
        ])
        consistency = agree_count / 2.0
        confidence = round(consistency * (0.4 + 0.6 * hf_score), 4)

        return {
            "post_id": post_id,
            "subreddit": subreddit,
            "text_snippet": (analyze_text[:200] + "...") if len(analyze_text) > 200 else analyze_text,
            "vader_compound": round(vader_scores["compound"], 4),
            "vader_pos": round(vader_scores["pos"], 4),
            "vader_neu": round(vader_scores["neu"], 4),
            "vader_neg": round(vader_scores["neg"], 4),
            "vader_label": vader_label,
            "hf_score": hf_score,
            "hf_label": hf_label,
            "combined_score": round(combined_score, 4),
            "overall_label": overall_label,
            "confidence": confidence,
            "analysis_timestamp": datetime.now().isoformat(),
        }


# ---------------------------------------------------------------------------
# Subreddit-level Aggregation
# ---------------------------------------------------------------------------

class SentimentAggregator:
    """Aggregate per-post sentiment into subreddit summaries."""

    @staticmethod
    def aggregate(
        post_results: list[dict[str, Any]],
    ) -> dict[str, dict[str, Any]]:
        """Group by subreddit and compute summary statistics."""
        groups: defaultdict[str, list[dict[str, Any]]] = defaultdict(list)
        for p in post_results:
            groups[p["subreddit"]].append(p)

        summaries: dict[str, dict[str, Any]] = {}
        for sr, posts in groups.items():
            compounds = [p.get("vader_compound", 0.0) for p in posts]
            combined_scores = [p.get("combined_score", 0.5) for p in posts]
            labels = Counter(p.get("overall_label", "neutral") for p in posts)

            positive_count = labels["positive"]
            negative_count = labels["negative"]
            total = len(posts)

            top_positive = sorted(
                [p for p in posts if p.get("overall_label") == "positive"],
                key=lambda x: x.get("combined_score", 0),
                reverse=True,
            )[:3]
            top_negative = sorted(
                [p for p in posts if p.get("overall_label") == "negative"],
                key=lambda x: x.get("combined_score", 0),
                reverse=False,
            )[:3]

            summaries[sr] = {
                "subreddit": sr,
                "post_count": total,
                "mean_compound": round(float(np.mean(compounds)), 4) if compounds else 0.0,
                "median_compound": round(float(np.median(compounds)), 4) if compounds else 0.0,
                "std_compound": round(float(np.std(compounds)) + 1e-9, 4) if compounds else 0.0,
                "mean_combined": round(float(np.mean(combined_scores)), 4) if combined_scores else 0.5,
                "positive_ratio": round(positive_count / total, 4) if total > 0 else 0.0,
                "negative_ratio": round(negative_count / total, 4) if total > 0 else 0.0,
                "neutral_ratio": round((total - positive_count - negative_count) / total, 4) if total > 0 else 1.0,
                "dominant_sentiment": labels.most_common(1)[0][0] if labels else "neutral",
                "sentiment_distribution": dict(labels),
                "top_positive_texts": [p.get("text_snippet", "") for p in top_positive],
                "top_negative_texts": [p.get("text_snippet", "") for p in top_negative],
            }

        return summaries


# ---------------------------------------------------------------------------
# Visualization Module
# ---------------------------------------------------------------------------

class SentimentVisualizer:
    """Create visual plots of sentiment analytics via Plotly."""

    @staticmethod
    def plot_subreddit_comparison(
        summaries: dict[str, dict[str, Any]],
        output_path: str = "sentiment_subreddit_comparison.png",
        bar_limit: int = 20,
    ) -> str:
        """Bar chart comparing mean sentiment across subreddits."""
        import plotly.graph_objects as go

        # Sort by mean_combined descending, take top `bar_limit`
        sorted_srs = sorted(summaries.keys(), key=lambda s: (-summaries[s]["mean_combined"], s))[:bar_limit]
        subreddits = list(sorted_srs)
        mean_combined = [summaries[s]["mean_combined"] for s in subreddits]

        # Color bars by sentiment direction
        colors = [
            "#2ecc71" if v >= 0.6 else "#e74c3c" if v <= 0.4 else "#95a5a6"
            for v in mean_combined
        ]

        height = max(400, len(subreddits) * 30)
        fig = go.Figure(data=[go.Bar(
            x=subreddits,
            y=mean_combined,
            marker_color=colors,
            text=[f"{v:.2f}" for v in mean_combined],
            textposition="outside",
        )])

        fig.update_layout(
            title="Mean Sentiment Score by Subreddit (0=neg -> 1=pos)",
            xaxis_title="Subreddit",
            yaxis_title="Mean Combined Score",
            yaxis_range=[0, 1],
            template="plotly_white",
            height=height,
            showlegend=False,
        )

        fig.add_hline(y=0.5, line_dash="dash", line_color="gray")
        os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
        fig.write_image(output_path, width=1200, height=height)
        logger.info("Bar chart saved to %s", output_path)
        return output_path

    @staticmethod
    def plot_sentiment_distribution(
        post_results: list[dict[str, Any]],
        output_path: str = "sentiment_distribution.png",
    ) -> str:
        """Histogram of sentiment scores for all subreddits combined."""
        import plotly.express as px

        # Map vader compound -1..1 to 0..1 and normalize with HF score
        scored = []
        for p in post_results:
            v_compound = p.get("vader_compound", 0.0)
            hf_score = p.get("hf_score", 0.5)
            combined = p.get("combined_score", None)
            if combined is not None:
                scored.append(combined)
            else:
                # Fallback: average of normalized vader + HF
                v_num = 0.5 + 0.5 * v_compound
                scored.append(0.5 * (v_num + hf_score))

        fig = px.histogram(
            x=scored,
            nbins=max(10, len(scored)),
            color_discrete_sequence=["#3498db"],
            marginal="box",
        )

        fig.update_layout(
            title="Sentiment Score Distribution (all subreddits)",
            xaxis_title="Sentiment Score",
            template="plotly_white",
        )
        os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
        fig.write_image(output_path, width=800)
        return output_path


# ---------------------------------------------------------------------------
# Report Formatter
# ---------------------------------------------------------------------------

class SentimentReportFormatter:
    """Format analysis results as JSON / CSV / Markdown."""

    @staticmethod
    def to_json(
        post_results: list[dict[str, Any]],
        summaries: dict | None = None,
    ) -> str:
        result = {
            "report_type": "sentiment_analysis",
            "generated_at": datetime.now().isoformat(),
            "post_count": len(post_results),
            "subreddit_count": len(set(p.get("subreddit", "unknown") for p in post_results)),
            "summary_statistics": summaries or {},
        }
        return json.dumps(result, indent=2)

    @staticmethod
    def to_csv(post_results: list[dict[str, Any]]) -> str:
        """Convert post results to CSV string."""
        header = (
            "post_id,subreddit,vader_compound,vader_pos,vader_neu,"
            "vader_neg,hf_score,hf_label,combined_score,overall_label,confidence"
        )
        if not post_results:
            return header + "\n"

        df = pd.DataFrame([{k: v for k, v in p.items()} for p in post_results])
        output = StringIO()
        df.to_csv(output, index=False)
        return output.getvalue()

    @staticmethod
    def to_markdown_summaries(
        summaries: dict[str, dict[str, Any]],
    ) -> str:
        lines = [
            "# Sentiment Analysis Report",
            f"\nGenerated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n",
            "## Subreddit Summaries\n",
            "| Subreddit | Posts | Mean Score | Pos % | Neg % | Neu % | Dominant |",
            "|---|---|---|---|---|---|---|",
        ]

        for sr, s in sorted(
            summaries.items(), key=lambda x: (-x[1].get("mean_combined", 0), x[0])
        ):
            lines.append(
                f"| {sr} "
                f"| {s.get('post_count', 0)} "
                f"| {s.get('mean_combined', 0):.3f} "
                f"| {(s.get('positive_ratio', 0) * 100):.1f}% "
                f"| {(s.get('negative_ratio', 0) * 100):.1f}% "
                f"| {(s.get('neutral_ratio', 0) * 100):.1f}% "
                f"| {s.get('dominant_sentiment', 'n/a')} |"
            )

        # Add top examples
        for sr, s in sorted(
            summaries.items(), key=lambda x: (-x[1].get("mean_combined", 0), x[0])
        ):
            if s.get("top_positive_texts"):
                lines.append(f"\n### {sr} - Top Positive Examples\n")
                for t in s["top_positive_texts"]:
                    snippet = re.sub(r'`', r'`+`', t)
                    display = f"`{snippet[:100]}...`" if len(t) > 100 else f"`{snippet}`"
                    lines.append(f"- + {display}")
            if s.get("top_negative_texts"):
                lines.append(f"\n### {sr} - Top Negative Examples\n")
                for t in s["top_negative_texts"]:
                    snippet = re.sub(r'`', r'`+`', t)
                    display = f"`{snippet[:100]}...`" if len(t) > 100 else f"`{snippet}`"
                    lines.append(f"- - {display}")

        return "\n".join(lines)


# ---------------------------------------------------------------------------
# Main Analysis Orchestration
# ---------------------------------------------------------------------------

class SentimentAnalysisPipeline:
    """End-to-end sentiment analysis pipeline."""

    def __init__(self, db_path: str | None = ".reddit_db.sqlite3"):
        self.db_path = db_path or ".reddit_db.sqlite3"
        self.results_cache: list[dict[str, Any]] = []
        self.summaries_cache: dict[str, dict[str, Any]] = {}

    def collect_posts(
        self,
        subreddit_filter: str | None = None,
        limit: int = 100,
    ) -> list[dict]:
        """Fetch posts from the Reddit DB (same schema as keyphrase module)."""
        from sqlite3 import connect as sql_connect

        conn = sql_connect(self.db_path)
        cursor = conn.cursor()

        if subreddit_filter:
            query = (
                "SELECT id, subreddit, title, body, created_utc "
                "FROM posts WHERE subreddit = ? LIMIT ?"
            )
            cursor.execute(query, (subreddit_filter, limit))
        else:
            query = (
                "SELECT id, subreddit, title, body, created_utc "
                "FROM posts LIMIT ?"
            )
            cursor.execute(query, (limit,))

        columns = [d[0] for d in cursor.description]  # type: ignore[union-attr]
        results: list[dict] = []
        for row in cursor.fetchall():
            row_dict = dict(zip(columns, row))
            row_dict.setdefault("title", "")
            row_dict.setdefault("body", "")
            texts = [str(row_dict["title"] or ""), str(row_dict["body"] or "")]
            combined = " ".join(t.strip() for t in texts if t)
            row_dict["_combined"] = combined[:512]
            results.append(row_dict)

        conn.close()
        return results

    def analyze(self, posts: list[dict]) -> list[dict[str, Any]]:
        """Run dual-engine sentiment analysis on a list of post dicts."""
        if not posts:
            logger.warning("No posts to analyze.")
            return []

        logger.info("Starting sentiment analysis on %d posts ...", len(posts))
        results: list[dict[str, Any]] = []
        total = len(posts)

        for i, post in enumerate(posts):
            try:
                title = str(post.get("title", ""))
                body = str(post.get("_combined", "") or "")
                sr = str(post.get("subreddit", "unknown"))
                pid = str(post.get("id", f"post_{i}"))

                analysis = CombinedSentimentAnalyzer.analyze_post(
                    post_id=pid,
                    subreddit=sr,
                    text=body,
                    title=title,
                )
                results.append(analysis)
            except Exception as exc:  # noqa: BLE001
                logger.error("Error analyzing post %s: %s", post.get("id", "?"), str(exc))
                results.append({
                    "post_id": str(post.get("id", f"error_{i}")),
                    "subreddit": str(post.get("subreddit", "unknown")),
                    "error": str(exc),
                })

            if (i + 1) % 50 == 0:
                logger.info("Analyzed %d/%d posts ...", i + 1, total)

        self.results_cache = results
        return results

    def aggregate(self) -> dict[str, dict[str, Any]]:
        if not self.results_cache:
            self.summaries_cache = SentimentAggregator.aggregate([])
        else:
            self.summaries_cache = SentimentAggregator.aggregate(self.results_cache)  # type: ignore[arg-type]
        return self.summaries_cache

    def export(
        self,
        output_dir: str | None = None,
        fmt: str = "all",
    ) -> dict[str, str]:
        """Export results to files. Returns {format -> filepath}."""
        outdir = output_dir or os.path.join(os.getcwd(), "output")
        os.makedirs(outdir, exist_ok=True)
        exported: dict[str, str] = {}

        if fmt in ("all", "json"):
            path = os.path.join(outdir, "sentiment_analysis_report.json")
            with open(path, "w") as fh:
                json.dump(
                    {
                        "report_type": "sentiment_analysis",
                        "generated_at": datetime.now().isoformat(),
                        "post_count": len(self.results_cache),
                        "subreddit_count": len(
                            set(p.get("subreddit", "unknown") for p in self.results_cache)
                        ),
                        "summary_statistics": self.summaries_cache,
                    },
                    fh,
                    indent=2,
                )
            exported["json"] = path

        if fmt in ("all", "csv"):
            path = os.path.join(outdir, "sentiment_analysis.csv")
            with open(path, "w", newline="") as fh:
                fh.write(self._to_csv_safe())
            exported["csv"] = path

        if fmt in ("all", "markdown"):
            path = os.path.join(outdir, "sentiment_analysis_report.md")
            with open(path, "w") as fh:
                fh.write(SentimentReportFormatter.to_markdown_summaries(self.summaries_cache))
            exported["markdown"] = path

        return exported

    # -- helpers (internal) -------------------------------------------------

    def _to_csv_safe(self) -> str:
        if not self.results_cache:
            return "post_id,subreddit\n"
        try:
            df = pd.DataFrame(
                [{k: v for k, v in p.items()} for p in self.results_cache]  # type: ignore[dict-item]
            )
            out = StringIO()
            df.to_csv(out, index=False)
            return out.getvalue()
        except Exception:  # noqa: BLE001
            header = "post_id,subreddit,vader_compound,hf_score,combined_score,overall_label\n"
            rows = "\n".join(
                f'{p.get("post_id","")},{p.get("subreddit","")},'
                f'{p.get("vader_compound","")},{p.get("hf_score","")},'
                f'{p.get("combined_score","")},{p.get("overall_label","")}'
                for p in self.results_cache
            )
            return header + rows

    def run(
        self,
        subreddit: str | None = None,
        limit: int = 200,
        output_dir: str | None = None,
    ) -> dict[str, Any]:
        """Run full pipeline: collect -> analyze -> aggregate -> export."""
        start_time = time.time()
        outdir = output_dir or os.path.join(os.getcwd(), "output")

        # Collect
        posts = self.collect_posts(subreddit_filter=subreddit, limit=limit)
        logger.info("Collected %d posts for analysis.", len(posts))

        if not posts:
            logger.warning("No posts collected - nothing to analyze.")
            return {"error": "no_posts", "subreddit_count": 0}

        # Analyze (VADER is instant; HF model may take a moment)
        results = self.analyze(posts)
        logger.info("Analysis complete for %d posts.", len(results))

        # Aggregate
        summaries = self.aggregate()
        logger.info("Aggregated into %d subreddit summaries.", len(summaries))

        # Export
        exported = self.export(output_dir=output_dir, fmt="all")

        # Visualize
        viz: dict[str, str] = {}
        try:
            if len(summaries) > 1:
                cmp_path = os.path.join(outdir or "output", "subreddit_comparison.png")
                vis_path = SentimentVisualizer.plot_subreddit_comparison(
                    summaries, output_path=cmp_path
                )
                viz["comparison_chart"] = vis_path

            # Distribution for subreddit with most posts
            top_sr = max(summaries.keys(), key=lambda s: summaries[s].get("post_count", 0))
            top_posts = [p for p in self.results_cache if p.get("subreddit") == top_sr]  # type: ignore[union-attr]
            if top_posts:
                dist_path = os.path.join(outdir or "output", "sentiment_distribution.png")
                vis_path = SentimentVisualizer.plot_sentiment_distribution(
                    top_posts, output_path=dist_path
                )
                viz["distribution"] = vis_path
        except Exception as exc:  # noqa: BLE001
            logger.error("Visualization failed (non-fatal): %s", str(exc))

        elapsed = time.time() - start_time
        return {
            "status": "success",
            "post_count": len(results),
            "subreddit_count": len(summaries),
            "summaries": summaries,
            "exported_files": exported,
            "visualizations": viz,
            "elapsed_seconds": round(elapsed, 2),
        }


# ---------------------------------------------------------------------------
# CLI Entry Point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Sentiment Analysis Pipeline for crawled Reddit content.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  %(prog)s --subreddit MachineLearning --limit 500\n"
            "  %(prog)s --database /path/to/reddit.db --output ./reports -a\n"
            "\n"
            "Sentiment scoring:\n"
            "  score >= 0.6 -> positive\n"
            "  score <= 0.4 -> negative\n"
            "  otherwise    -> neutral\n"
        ),
    )

    parser.add_argument(
        "--subreddit", "-s", help="Specific subreddit to analyze (omit for all).", default=None
    )
    parser.add_argument(
        "--database", "-d", help="Path to the SQLite Reddit database.", default=".reddit_db.sqlite3"
    )
    parser.add_argument(
        "--output", "-o", help="Output directory for reports.", default="./output"
    )
    parser.add_argument(
        "--limit", "-l", help="Maximum number of posts per subreddit to analyze.", type=int, default=200
    )
    parser.add_argument(
        "--hf-model", help="Path to a custom HuggingFace sentiment model.", default=None
    )
    parser.add_argument(
        "--all-subreddits", "-a", action="store_true",
        help="Analyze all subreddits in the database (default behavior).",
    )

    args = parser.parse_args()

    # Initialize engines
    VaderAnalyzer.initialize()
    TransformersSentimentAnalyzer.initialize(model_name=args.hf_model)
    logger.info("All sentiment analysis engines initialized.")

    pipeline = SentimentAnalysisPipeline(db_path=args.database)
    report = pipeline.run(subreddit=args.subreddit, limit=args.limit, output_dir=args.output)

    if report.get("status") == "success":
        print(f"  Posts analyzed : {report['post_count']}")
        print(f"  Subreddits     : {report['subreddit_count']}")
        for fmt, fpath in report["exported_files"].items():
            print(f"  Report ({fmt})  : {fpath}")
        for vis_fmt, vis_path in report.get("visualizations", {}).items():
            print(f"  Viz ({vis_fmt}): {vis_path}")
    else:
        print(f"\nNo results produced. Check database connectivity.")

    return report


if __name__ == "__main__":
    main()
