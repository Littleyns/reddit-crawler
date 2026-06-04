"""Sentiment Analysis Pipeline -- Rotation Task A

Dual-engine sentiment scoring:
  1. VADER (lexicon-based) – fast, no ML download needed
  2. HuggingFace transformer (pipeline.sentiment-analysis) – contextual, more accurate

Supports per-thread and per-subreddit aggregated scores with full compound/pos/neu/neg breakdowns.

Usage:
    from sentiment.sentiment_pipeline import SentimentPipeline

    pipeline = SentimentPipeline()
    result = pipeline.analyze(threads)

    # Per-thread access
    for tsent in result.thread_sentiments[:5]:
        print(f"{tsent.title} -> {tsent.vader_composite:.3f} / {tsent.transformer_label:.3f}")

    # Per-subreddit aggregation
    for sub, ssent in result.per_subreddit.items():
        print(f"r/{sub}: VADER_compound={ssent.mean_vader:.4f}, "
              f"HF_mean={ssent.mean_hf_score:.4f} ({ssent.n_threads} threads)")

Dependencies: vaderSentiment, transformers >= 4.0

First HF run downloads the model (~400 MB) from HuggingFace to ~/.cache/huggingface.
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass, field, asdict
from typing import Optional


# --------------------------------------------------------------------------- #
#  Data classes                                                               #
# --------------------------------------------------------------------------- #

@dataclass
class ThreadSentiment:
    """Sentiment scores for a single thread."""
    thread_id: str
    subreddit: str
    title: str
    vader_positive: float
    vader_neutral: float
    vader_negative: float
    vader_composite: float       # [-1, +1] where + is positive
    transformer_label: Optional[str] = None   # "POSITIVE", "NEGATIVE", "NEUTRAL"
    transformer_score: Optional[float] = None  # confidence [0, 1]
    compound_avg: Optional[float] = None        # (vader_composite + hf_norm) / 2


@dataclass
class SubredditSentiment:
    """Aggregated sentiment stats for one subreddit."""
    subreddit: str
    n_threads: int
    mean_vader: float          # mean compound [-1, +1]
    std_vader: float
    mean_hf_score: float       # mean HF confidence score [0, 1]
    positive_count: int        # threads with composite > 0.05
    negative_count: int        # threads with composite < -0.05
    neutral_count: int         # rest

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class SentimentResult:
    """Full output of a sentiment analysis run."""
    per_subreddit: dict[str, SubredditSentiment]
    thread_sentiments: list[ThreadSentiment]
    total_threads: int
    global_mean_vader: float = 0.0
    global_positive_pct: float = 0.0
    global_negative_pct: float = 0.0
    global_neutral_pct: float = 0.0


# --------------------------------------------------------------------------- #
#  Core pipeline                                                              #
# --------------------------------------------------------------------------- #

class SentimentPipeline:
    """Dual-engine sentiment analysis (VADER + HuggingFace Transformers).

    Attributes:
        use_vader:     Enable lexicon-based scoring via VADER (default True)
        use_transformer: Enable contextual scoring via HF transformers (default True)
        composite_weight: Weight for vader component in compound score.
                          transformer gets (1 - composite_weight).
        label_threshold: Threshold for "neutral" when using composite.
    """

    LABEL_THRESHOLD_COMPOSITE = 0.05       # |compound| < this => neutral
    COMPOSITE_WEIGHT_VADER = 0.6           # Weight given to VADER in compound score

    def __init__(
        self,
        use_vader: bool = True,
        use_transformer: bool = True,
        composite_weight: float | None = None,
        label_threshold: float | None = None,
    ) -> None:
        self.use_vader = use_vader
        self.use_transformer = use_transformer

        if composite_weight is not None:
            self.COMPOSITE_WEIGHT_VADER = composite_weight
        if label_threshold is not None:
            self.LABEL_THRESHOLD_COMPOSITE = label_threshold

    # ---- public API ---------------------------------------------------------

    def analyze(
        self,
        threads: list[dict],
        batch_size_transformer: int = 32,
    ) -> SentimentResult:
        """Run sentiment analysis on crawled threads.

        Args:
            threads: List of dicts with at least `id`, `subreddit`, `title`.
                     Optional keys: `body` (str text).
            batch_size_transformer: Batch size for HF pipeline inference.

        Returns:
            SentimentResult containing per-thread and aggregated per-subreddit data.
        """
        # Lazy init VADER / transformer only when first called
        if self.use_vader and not hasattr(self, "_vader"):
            try:
                from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer
                self._vader = SentimentIntensityAnalyzer()
            except ImportError:
                raise ImportError(
                    "vaderSentiment is required for sentiment analysis.\n"
                    "    pip install vaderSentiment"
                )

        if self.use_transformer and not hasattr(self, "_hf_pipe"):
            try:
                from transformers import pipeline
                print("  [HF] Loading sentiment-analysis pipeline ...")
                # Use distilbert-base-uncased-finetuned-sst-2-english for speed/accuracy balance.
                self._hf_pipe = pipeline(
                    "sentiment-analysis",
                    model="distilbert/distilbert-base-uncased-finetuned-sst-2-english",
                    tokenizer="distilbert/distilbert-base-uncased-finetuned-sst-2-english",
                )
                print("  [HF] Pipeline loaded OK.")
            except ImportError:
                raise ImportError(
                    "transformers is required for transformer-based sentiment.\n"
                    "    pip install transformers torch vaderSentiment"
                )

        # Phase 1 – per-thread scoring
        thread_results: list[ThreadSentiment] = []
        for t in threads:
            tsent = self._score_thread(t)
            thread_results.append(tsent)

        # Phase 2 – aggregate per subreddit
        sub_acc: dict[str, list[float]] = {}    # subreddit → [composite_scores]
        sub_hf_acc: dict[str, list[float]] = {} # subreddit → [hf_score_norm]
        for ts in thread_results:
            sub = ts.subreddit
            sub_acc.setdefault(sub, []).append(ts.composite_avg or 0.0)
            if ts.transformer_score is not None and ts.vader_composite is not None:
                hf_scaled = (ts.transformer_score * 2) - 1  # map [0,1] → [-1,+1]
                sub_hf_acc.setdefault(sub, []).append(hf_scaled)

        per_subreddit: dict[str, SubredditSentiment] = {}
        for sub in sorted(set(
            [ts.subreddit for ts in thread_results if ts.vader_composite == 0.0 or ts.vader_composite == ts.vader_composite]  # isfinite guard
        )):
            vader_scores = sub_acc.get(sub, [])
            hf_scores = sub_hf_acc.get(sub, [])

            n = len(vader_scores)
            if n == 0:
                continue

            mean_v = float(sum(vader_scores)) / n
            std_v = math.sqrt(max(0.0, (sum((v - mean_v)**2 for v in vader_scores)) / max(n - 1, 1)))

            hf_mean = (float(sum(hf_scores)) / len(hf_scores)) if hf_scores else 0.0

            pos_c = sum(1 for v in vader_scores if v > self.LABEL_THRESHOLD_COMPOSITE)
            neg_c = sum(1 for v in vader_scores if v < -self.LABEL_THRESHOLD_COMPOSITE)
            neu_c = n - pos_c - neg_c

            per_subreddit[sub] = SubredditSentiment(
                subreddit=sub,
                n_threads=n,
                mean_vader=round(mean_v, 6),
                std_vader=round(std_v, 6),
                mean_hf_score=round(hf_mean, 6),
                positive_count=pos_c,
                negative_count=neg_c,
                neutral_count=neu_c,
            )

        # Global stats
        all_composites = [ts.composite_avg or 0.0 for ts in thread_results]
        g_mean = float(sum(all_composites)) / len(all_composites) if all_composites else 0.0
        g_pos = sum(1 for v in all_composites if v > self.LABEL_THRESHOLD_COMPOSITE) / max(len(all_composites), 1) * 100
        g_neg = sum(1 for v in all_composites if v < -self.LABEL_THRESHOLD_COMPOSITE) / max(len(all_composites), 1) * 100
        g_neu = 100 - g_pos - g_neg

        return SentimentResult(
            per_subreddit=per_subreddit,
            thread_sentiments=thread_results,
            total_threads=len(thread_results),
            global_mean_vader=round(g_mean, 6),
            global_positive_pct=round(g_pos, 2),
            global_negative_pct=round(g_neg, 2),
            global_neutral_pct=round(g_neu, 2),
        )

    # ---- per-thread scoring -------------------------------------------------

    def _score_thread(self, thread: dict) -> ThreadSentiment:
        """Score a single thread through both engines and return combined result."""
        title = thread.get("title", "") or ""
        body = (thread.get("body") or "").strip()
        text = f"{title} {body[:300]}".strip() or title

        vader_comp = 0.0
        vader_pos = 0.0
        vader_neu = 0.0
        vader_neg = 0.0

        if self.use_vader:
            scores = self._vader.polarity_scores(text)
            vader_comp = scores["compound"]
            vader_pos = scores["pos"]
            vader_neu = scores["neu"]
            vader_neg = scores["neg"]

        hf_label: Optional[str] = None
        hf_score: Optional[float] = None
        if self.use_transformer and hasattr(self, "_hf_pipe"):
            result = self._hf_pipe(text[:512], batch_size=1, truncation=True)  # type: ignore[union-attr, arg-type]
            hf_label = result[0]["label"]   # "POSITIVE" | "NEGATIVE" | "NEUTRAL"
            hf_score = result[0]["score"]    # confidence

        # Composite score = weighted mean of vader_compound + scaled HF score
        composite: float
        if self.use_vader and self.use_transformer and hf_score is not None:
            hf_scaled = (hf_score * 2) - 1       # [0,1] → [-1,+1]
            composite = (self.COMPOSITE_WEIGHT_VADER * vader_comp
                         + (1 - self.COMPOSITE_WEIGHT_VADER) * hf_scaled)
        elif hf_score is not None:
            composite = hf_scaled
        else:
            composite = vader_comp

        return ThreadSentiment(
            thread_id=thread.get("id", "unknown"),
            subreddit=thread.get("subreddit", "unknown"),
            title=title,
            vader_positive=vader_pos,
            vader_neutral=vader_neu,
            vader_negative=vader_neg,
            vader_composite=vader_comp,
            transformer_label=hf_label,
            transformer_score=hf_score,
            compound_avg=round(composite, 6),
        )

    # ---- convenience helpers ------------------------------------------------

    def summarize(self, result: SentimentResult) -> str:
        """Return a human-readable summary of sentiment analysis."""
        lines: list[str] = []
        lines.append("=" * 70)
        lines.append("📊 Sentiment Analysis Summary")
        lines.append("=" * 70)
        lines.append(f"Total threads analyzed: {result.total_threads}")
        lines.append(f"Global mean compound (composite): {result.global_mean_vader:.4f}")
        lines.append(f"Positive: {result.global_positive_pct:.1f}% | "
                      f"Neutral: {result.global_neutral_pct:.1f}% | "
                      f"Negative: {result.global_negative_pct:.1f}%")
        lines.append("-" * 70)
        lines.append("\nPer-Subreddit Breakdown:")
        lines.append(f"{'<Subreddit':<20} {'Threads':>8} {'Mean VADER':>11} "
                       f"{'Mean HF':>9} {'%+':>6} {'%-':>6}")
        for sub in sorted(result.per_subreddit):
            ss = result.per_subreddit[sub]
            pct_pos = ss.positive_count / max(ss.n_threads, 1) * 100
            pct_neg = ss.negative_count / max(ss.n_threads, 1) * 100
            lines.append(
                f"r/{sub:<19} {ss.n_threads:>8} {ss.mean_vader:>11.4f} "
                f"{ss.mean_hf_score:>9.4f} {pct_pos:>6.1f} {pct_neg:>5.1f}"
            )
        lines.append("")
        
        # Most positive/negative threads
        sorted_by = sorted(result.thread_sentiments, key=lambda t: t.composite_avg or 0, reverse=True)
        lines.append("Top 5 most positive threads:")
        for ts in sorted_by[:5]:
            marker = "🟢" if (ts.composite_avg or 0) > 0 else "⚪"
            label = f"[{ts.transformer_label}] " if ts.transformer_label else ""
            lines.append(f"  {marker} r/{ts.subreddit} \"{ts.title[:55]}…\"  comp={ts.composite_avg:.3f}  {label}")

        sorted_by_neg = sorted(result.thread_sentiments, key=lambda t: t.composite_avg or 0)
        lines.append("\nTop 5 most negative threads:")
        for ts in sorted_by_neg[:5]:
            marker = "🔴" if (ts.composite_avg or 0) < 0 else "⚪"
            label = f"[{ts.transformer_label}] " if ts.transformer_label else ""
            lines.append(f"  {marker} r/{ts.subreddit} \"{ts.title[:55]}…\"  comp={ts.composite_avg:.3f}  {label}")
        lines.append("")

        return "\n".join(lines)


# --------------------------------------------------------------------------- #
#  Demo                                                                       #
# --------------------------------------------------------------------------- #

def run_example() -> None:
    """Demo sentiment analysis on sample data."""
    print("=" * 60)
    print("📊 Sentiment Pipeline - Demo")
    print("=" * 60)

    sample_threads = [
        {
            "id": "1", "subreddit": "python",
            "title": "Amazing new type hint feature in Python 3.12!",
            "body": "PEP 695 makes TypeParam so much cleaner. Love it.",
        },
        {
            "id": "2", "subreddit": "python",
            "title": "Deprecation warnings everywhere after upgrading to pandas 2.0",
            "body": "So many breaking changes and I just want to write a simple CSV parser.",
        },
        {
            "id": "3", "subreddit": "datascience",
            "title": "Best practices for feature engineering with large datasets",
            "body": "Looking for tips on handling sparse categorical features in production ML pipelines.",
        },
        {
            "id": "4", "subreddit": "reactjs",
            "title": "Server components are a game changer for Next.js performance",
            "body": "Migrated our app to the App Router and page loads dropped by 60%.",
        },
        {
            "id": "5", "subreddit": "machinelearning",
            "title": "Transformer attention mechanisms explained - a comprehensive guide",
            "body": "Written for beginners who want to understand how self-attention works from scratch.",
        },
        {
            "id": "6", "subreddit": "python",
            "title": "Why does asyncio.run() block the event loop in this weird edge case?",
            "body": "I have a blocking HTTP call inside a coroutine and nothing seems to help.",
        },
        {
            "id": "7", "subreddit": "datascience",
            "title": "Terrible experience with data cleaning - it takes 90% of the time",
            "body": "Just spent 3 hours dealing with encoding issues in a CSV file. Data science is mostly plumbing.",
        },
    ]

    pipeline = SentimentPipeline()
    result = pipeline.analyze(sample_threads)
    print(pipeline.summarize(result))
    return result


if __name__ == "__main__":
    run_example()
