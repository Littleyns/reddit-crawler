"""
Sentiment Analysis Pipeline — Task A

Unified sentiment analysis for Reddit threads using:
  1. VADER (NLTK) — fast, lexicon-based scoring per thread
  2. Transformer-based classifier (HF pipeline) — fallback when models are unavailable
     (gracefully degrades to VADER only)

Outputs per-thread sentiment scores:
  - compound (±1), pos, neg, neu polarity breakdowns for both title and body
  - Overall aggregated compound score with polarity label
  - Per-comment sentiment where available

Usage:
  python -m src.sentiment_analyzer --csv data/crawled_threads.csv --output results/sentiment_scores.csv
  # Or programmatically:
  from sentiment_analyzer import SentimentPipeline
  pipe = SentimentPipeline()
  scores = pipe.analyze_thread(title="...", body="...")
"""

from __future__ import annotations

import argparse
import csv
import json
import logging
import os
import sys
from dataclasses import asdict, dataclass, field
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

import pandas as pd
from nltk.sentiment.vader import SentimentIntensityAnalyzer

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# HuggingFace optional import (may fail in constrained environments)
# ---------------------------------------------------------------------------
try:  # noqa: C901 (pipeline is intentionally comprehensive; not a candidate further)
    from transformers import pipeline as hf_pipeline, AutoTokenizer, AutoModelForSequenceClassification  # type: ignore[import-untyped]

    _HF_AVAILABLE = True
except ImportError:
    _HF_AVAILABLE = False
except Exception as exc:
    logger.warning("HuggingFace loaded but unusable (no PyTorch?): %s", exc)
    _HF_AVAILABLE = False


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass
class CommentSentiment:
    """Sentiment score for a single comment on a thread."""
    author: str = ""
    text: str = ""
    compound: float = 0.0
    pos: float = 0.0
    neg: float = 0.0
    neu: float = 0.0
    label: str = "neutral"

    def to_dict(self) -> Dict[str, Any]:
        return {
            "author": self.author,
            "compound": round(self.compound, 4),
            "pos": round(self.pos, 4),
            "neg": round(self.neg, 4),
            "neu": round(self.neu, 4),
            "label": self.label,
        }


@dataclass
class ThreadSentiment:
    """Aggregated sentiment score for a single Reddit thread."""

    thread_id: str = ""
    subreddit: str = ""
    title: str = ""
    title_compound: float = 0.0
    title_pos: float = 0.0
    title_neg: float = 0.0
    title_neu: float = 0.0
    body_compound: float = 0.0
    body_pos: float = 0.0
    body_neg: float = 0.0
    body_neu: float = 0.0
    overall_compound: float = 0.0
    polarity_label: str = "neutral"
    comment_count: int = 0
    avg_comment_compound: float = 0.0
    comments: List[CommentSentiment] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "thread_id": self.thread_id,
            "subreddit": self.subreddit,
            "polarity_label": self.polarity_label,
            "overall_compound": round(self.overall_compound, 4),
            "title_compound": round(self.title_compound, 4),
            "title_pos": round(self.title_pos, 4),
            "title_neg": round(self.title_neg, 4),
            "body_compound": round(self.body_compound, 4),
            "comment_count": self.comment_count,
            "avg_comment_compound": round(self.avg_comment_compound, 4),
        }


# ---------------------------------------------------------------------------
# SentimentPipeline — main orchestrator
# ---------------------------------------------------------------------------

class SentimentPipeline:
    """Analyzes Reddit threads and their comments for sentiment.

    Strategy:
      - Always runs VADER (fast, reliable).
      - If a HuggingFace pipeline is available, also scores with it
        and returns an ensemble of both models (mean compound).
    """

    TITLE_MAX_CHARS = 500   # VADER handles long strings poorly; truncate safely
    BODY_MAX_CHARS = 3000

    def __init__(self) -> None:
        self.vader = SentimentIntensityAnalyzer()
        self._hf_pipe: Any | None = None
        if _HF_AVAILABLE:
            try:
                self._hf_pipe = hf_pipeline(
                    "sentiment-analysis",
                    model="distilbert-base-uncased-finetuned-sst-2-english",
                    tokenizer="distilbert-base-uncased",
                )
                logger.info("HuggingFace sentiment pipeline loaded.")
            except Exception as exc:
                logger.warning("Could not load HF pipeline: %s — falling back to VADER only.", exc)
                self._hf_pipe = None

    # ---- helpers ----------------------------------------------------------

    @staticmethod
    def _compound_label(compound: float) -> str:
        if compound >= 0.05:
            return "positive"
        if compound <= -0.05:
            return "negative"
        return "neutral"

    # ---- scoring ----------------------------------------------------------

    def _score_with_vader(self, text: str) -> Tuple[float, Dict[str, float]]:
        """Return (compound, raw_scores_dict)."""
        scores = self.vader.polarity_scores(text[:self.TITLE_MAX_CHARS])
        return scores["compound"], {
            "pos": scores["pos"],
            "neg": scores["neg"],
            "neu": scores["neu"],
        }

    def _score_with_hf(self, text: str) -> float | None:
        """Return compound-like 0-1 probability for positive class, or None on failure."""
        if not self._hf_pipe:
            return None
        try:
            # HF pipeline returns [{"label": "POSITIVE"/"NEGATIVE", "score": 0.0-1.0}]
            result = self._hf_pipe(text[:self.TITLE_MAX_CHARS])
            entry = result[0] if isinstance(result, list) else result
            score_float: float = float(entry["score"])
            label_str: str = str(entry.get("label", "")).upper()
            # Transform to -1..1 compound scale
            if "NEGATIVE" in label_str:
                return -(1.0 - score_float)  # negative polarity
            return score_float  # positive polarity
        except Exception as exc:
            logger.warning("HF scoring failed: %s", exc)
            return None

    def analyze_thread(
        self,
        thread_id: str = "",
        subreddit: str = "",
        title: str = "",
        body: str = "",
        comments: Optional[List[Dict[str, str]]] = None,  # list of {"author": ..., "text": ...}
    ) -> ThreadSentiment:
        """Run sentiment analysis on a single thread and its comments.

        Args:
            thread_id: Reddit post ID (e.g., 't3_abc123')
            subreddit: e.g. 'Python' or 'MachineLearning'
            title: Thread title string
            body: Thread selftext / description
            comments: Optional list of comment dicts {author, text}

        Returns:
            ThreadSentiment with all scores populated.
        """
        # --- Title --------------------------------------------------------
        vader_title_compound, vader_title_scores = self._score_with_vader(title)
        hf_title_score = self._score_with_hf(title)

        if hf_title_score is not None:
            title_compound = (vader_title_compound + hf_title_score) / 2.0
        else:
            title_compound = vader_title_compound

        overall_pos_scores = self.vader.polarity_scores(title)[:3] if False else {}
        # Recompute full VADER scores for title to get pos/neg/neu
        _, full_vader_title = self._score_with_vader(title)

        # --- Body ---------------------------------------------------------
        vader_body_compound, vader_body_scores = self._score_with_vader(body)
        hf_body_score = self._score_with_hf(body) if body else None

        if hf_body_score is not None:
            body_compound = (vader_body_compound + hf_body_score) / 2.0
        else:
            body_compound = vader_body_compound

        _, full_vader_body = self._score_with_vader(body)

        # --- Comments -----------------------------------------------------
        parsed_comments: List[CommentSentiment] = []
        if comments:
            compounds = []
            for cmt in comments:
                text = cmt.get("text", "") or ""
                author = cmt.get("author", "[unknown]") or "unknown"
                compound, pos_dict = self._score_with_vader(text)
                label_str = self._compound_label(compound)
                parsed_comments.append(CommentSentiment(
                    author=author,
                    text=text[:200],  # truncate for CSV safety
                    compound=compound,
                    pos=pos_dict["pos"],
                    neg=pos_dict["neg"],
                    neu=pos_dict["neu"],
                    label=label_str,
                ))
                compounds.append(compound)

            avg_comment_compound = sum(compounds) / len(compounds) if compounds else 0.0
        else:
            avg_comment_compound = 0.0

        # --- Overall aggregate --------------------------------------------
        components = [title_compound, body_compound] + ([c.compound for c in parsed_comments] if parsed_comments else [])
        overall_compound = sum(components) / len(components) if components else 0.0

        return ThreadSentiment(
            thread_id=thread_id or "",
            subreddit=subreddit or "",
            title=title[:200],
            title_compound=title_compound,
            title_pos=full_vader_title.get("pos", 0),
            title_neg=full_vader_title.get("neg", 0),
            title_neu=full_vader_title.get("neu", 0),
            body_compound=body_compound,
            body_pos=full_vader_body.get("pos", 0),
            body_neg=full_vader_body.get("neg", 0),
            body_neu=full_vader_body.get("neu", 0),
            overall_compound=overall_compound,
            polarity_label=self._compound_label(overall_compound),
            comment_count=len(parsed_comments),
            avg_comment_compound=avg_comment_compound,
            comments=parsed_comments[:50],  # cap for memory safety in CSV
        )

    def analyze_batch(
        self,
        threads: List[Dict[str, Any]],
    ) -> List[ThreadSentiment]:
        """Run sentiment on a list of thread dicts from crawled data.

        Expects each dict to contain at minimum: id, subreddit, title.
        Optional keys: body, comments (list of {author, text}).
        """
        results: List[ThreadSentiment] = []
        for i, thread in enumerate(threads):
            try:
                score = self.analyze_thread(
                    thread_id=str(thread.get("id", f"unknown_{i}")),
                    subreddit=str(thread.get("subreddit", "unknown")),
                    title=str(thread.get("title", "")),
                    body=str(thread.get("body", "")),
                    comments=thread.get("comments"),
                )
                results.append(score)
            except Exception as exc:
                logger.error("Failed to analyze thread %s: %s", thread.get("id", i), exc)
        return results


# ---------------------------------------------------------------------------
# Export utilities
# ---------------------------------------------------------------------------

def save_to_csv(results: List[ThreadSentiment], filepath: str) -> int:
    """Save sentiment scores to CSV. Returns number of rows written."""
    fieldnames = [
        "thread_id", "subreddit", "polarity_label", "overall_compound",
        "title_compound", "title_pos", "title_neg",
        "body_compound", "comment_count", "avg_comment_compound",
    ]
    os.makedirs(os.path.dirname(filepath) or ".", exist_ok=True)
    with open(filepath, "w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=fieldnames)
        writer.writeheader()
        for r in results:
            writer.writerow(r.to_dict())
    return len(results)


def save_to_jsonl(results: List[ThreadSentiment], filepath: str) -> int:
    """Save sentiment scores to JSONL (every thread = one line)."""
    os.makedirs(os.path.dirname(filepath) or ".", exist_ok=True)
    with open(filepath, "w", encoding="utf-8") as fh:
        for r in results:
            line = json.dumps(r.to_dict())
            # Also dump comment-level data if any are present
            if r.comments:
                full = {**r.to_dict(), "comments": [c.to_dict() for c in r.comments[:30]]}
                fh.write(json.dumps(full))
            else:
                fh.write(line)
            fh.write("\n")
    return len(results)


def summarize_sentiment(results: List[ThreadSentiment]) -> Dict[str, Any]:
    """Return aggregate summary stats by subreddit."""
    if not results:
        return {"total_threads": 0, "message": "No threads to summarize"}

    positives = sum(1 for r in results if r.polarity_label == "positive")
    negatives = sum(1 for r in results if r.polarity_label == "negative")
    neutrals = len(results) - positives - negatives
    avg_compound = sum(r.overall_compound for r in results) / len(results)

    # Per-subreddit breakdown
    by_sub: Dict[str, List[float]] = {}
    for r in results:
        by_sub.setdefault(r.subreddit, []).append(r.overall_compound)

    sub_summary: Dict[str, Dict[str, float]] = {}
    for sub, scores in by_sub.items():
        n = len(scores)
        pos_count = sum(1 for s in scores if s >= 0.05)
        neg_count = sum(1 for s in scores if s <= -0.05)
        neu_count = n - pos_count - neg_count
        std_val = (sum((s - avg(scores)) ** 2 for s in scores) / n) ** 0.5
        sub_summary[sub] = {
            "count": n,
            "avg_compound": round(avg_compound if n == 1 else sum(scores) / n, 4),
            "positive_pct": round(pos_count / n * 100, 1),
            "negative_pct": round(neg_count / n * 100, 1),
            "neutral_pct": round(neu_count / n * 100, 1),
            "std_compound": round(std_val, 4),
        }

    return {
        "generated_at": datetime.now().isoformat(),
        "total_threads": len(results),
        "positive_count": positives,
        "negative_count": negatives,
        "neutral_count": neutrals,
        "overall_avg_compound": round(avg_compound, 4),
        "by_subreddit": sub_summary,
    }


def avg(values):
    return sum(values) / len(values) if values else 0.0


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main() -> None:     # pylint: disable=C901
    parser = argparse.ArgumentParser(description="Sentiment Analysis Pipeline")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--csv", help="Input CSV of crawled threads")
    group.add_argument("--json", help="Input JSON file with thread list")
    group.add_argument("--demo", action="store_true", help="Run demo on sample data")
    parser.add_argument("--output-dir", default="results/sentiment", help="Output directory (default: results/sentiment)")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose logging")
    args = parser.parse_args()

    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO)

    pipeline = SentimentPipeline()

    # ---- load threads ----------------------------------------------------
    threads: List[Dict[str, Any]] = []

    if args.demo:
        threads = _sample_threads()
        output_dir = os.path.join(args.output_dir or "results/sentiment", "demo")
    elif args.csv:
        df = pd.read_csv(args.csv)
        for _, row in df.iterrows():
            comments_raw = row.get("comments_json")
            if isinstance(comments_raw, str):
                try:
                    comments_list = json.loads(comments_raw)
                except json.JSONDecodeError:
                    comments_list = []
            else:
                comments_list = [] or None
            threads.append({
                "id": str(row.get("id", row.get("thread_id", ""))),
                "subreddit": str(row.get("subreddit", "")),
                "title": str(row.get("title", "")),
                "body": str(row.get("body", row.get("selftext", "") or "")),
                "comments": comments_list,
            })
        output_dir = os.path.join(args.output_dir or "results/sentiment", "csv")
    elif args.json:
        with open(args.json, "r", encoding="utf-8") as fh:
            data = json.load(fh)
        threads = data if isinstance(data, list) else data.get("threads", [])
        for t in threads:
            if "comments" not in t:
                t["comments"] = t.get("replies", [])
        output_dir = os.path.join(args.output_dir or "results/sentiment", "json")

    logger.info("Loaded %d threads from %s.", len(threads), "demo/csv/json")

    # ---- analyze ---------------------------------------------------------
    results = pipeline.analyze_batch(threads)
    save_to_csv(results, os.path.join(output_dir, "sentiment_scores.csv"))
    save_to_jsonl(results, os.path.join(output_dir, "sentiment_scores.jsonl"))

    summary = summarize_sentiment(results)
    with open(os.path.join(output_dir, "summary.json"), "w", encoding="utf-8") as fh:
        json.dump(summary, fh, indent=2)

    logger.info(
        "Analysis complete: %d threads analyzed | positive=%d negative=%d neutral=%d overall_avg=%.4f",
        len(results),
        summary["positive_count"],
        summary["negative_count"],
        summary["neutral_count"],
        summary["overall_avg_compound"],
    )

    print(f"\n✅ Sentiment scores saved to: {os.path.abspath(args.output_dir)}\n")


def _sample_threads() -> List[Dict[str, Any]]:     # pylint: disable=C901 (demo helper, not production)
    """Return sample threads for demonstration."""
    return [
        {
            "id": "demo_001",
            "subreddit": "Python",
            "title": "Python 3.13 is absolutely fantastic! New features are amazing.",
            "body": "Just upgraded to 3.13 and the performance improvements are incredible. The new exception groups and except* syntax are game changers for error handling. Really happy with this update!",
            "comments": [
                {"author": "dev_guru", "text": "Totally agree! Python has been improving so much."},
                {"author": "old_hacker", "text": "I'm still on 3.9, afraid to upgrade. Breaking changes scare me."},
                {"author": "jupyter_fan", "text": "The new timing module is awesome, great job devs!"},
            ],
        },
        {
            "id": "demo_002",
            "subreddit": "MachineLearning",
            "title": "Transformer models are becoming too computationally expensive for most research labs",
            "body": "The cost of training GPT-class models is astronomical. We need better efficiency or open-source alternatives that don't require 100k GPUs. Budget constraints are real.",
            "comments": [
                {"author": "ml_researcher", "text": "Small language models show promise but still far from transformer quality."},
                {"author": "industry_vet", "text": "Already using distilled models in production with great results."},
                {"author": "phd_student", "text": "It's really discouraging for smaller institutions. Funding is a huge issue."},
            ],
        },
        {
            "id": "demo_003",
            "subreddit": "datascience",
            "title": "Happy to share my journey from bootcamp to senior data scientist",
            "body": "",
            "comments": [
                {"author": "bootcamper2025", "text": "Inspiring! What was your most valuable learning resource?"},
                {"author": "ds_lead", "text": "Congrats on the promotion! Keep up the great work."},
            ],
        },
    ]


if __name__ == "__main__":     # pylint: disable=C901 (CLI entry; intentionally large)
    main()
