"""
Sentiment Analysis Pipeline -- Rotation Task A

Dual-backend sentiment analysis for crawled Reddit threads.

Backends:
    "vader"          Lexicon-based (VADER), no model download, instant results.
                     Good for rapid prototyping and large-scale filtering.
    "transformers"   Contextual transformer model (pipeline) via HuggingFace.
                     Higher accuracy, captures nuance/context in Reddit language.

Output: per-thread sentiment scores aggregated by subreddit with overall statistics.

Usage:
    >>> from sentiment.sentiment_pipeline import SentimentPipeline
    >>> threads = [
    ...     {"id": "1", "subreddit": "python", "title": "Python is great!", "body": "..."},
    ...     {"id": "2", "subreddit": "python", "title": "This is terrible.", "body": "..."},
    ... ]
    >>> pipeline = SentimentPipeline(backend="vader", threshold=0.1)
    >>> result = pipeline.analyze(threads)
    >>> print(result.summary())

Dependencies (VADER):  pip install vaderSentiment nltk
Dependencies (HF):     pip install transformers torch sentencepiece
"""

from __future__ import annotations

import re
import statistics
from dataclasses import dataclass, field, asdict
from enum import Enum
from typing import Optional


# ────────────────────────────── Data classes ──────────────────────────────


class SentimentLabel(str, Enum):
    """Discrete sentiment labels derived from continuous scores."""
    POSITIVE = "positive"
    NEUTRAL = "neutral"
    NEGATIVE = "negative"


@dataclass
class ThreadSentiment:
    """Sentiment score for a single thread/post."""
    thread_id: str
    subreddit: str
    label: SentimentLabel      # positive / neutral / negative
    score: float               # continuous sentiment score (backend-dependent)
    title: str = ""            # sanitized original title
    body_preview: str = ""     # first 80 chars of body

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class SubredditSentiment:
    """Aggregated sentiment stats for one subreddit."""
    subreddit: str
    total_threads: int = 0
    positive_count: int = 0
    neutral_count: int = 0
    negative_count: int = 0
    mean_score: float = 0.0
    median_score: float = 0.0
    std_score: float = 0.0
    min_score: float = 0.0
    max_score: float = 0.0

    def to_dict(self) -> dict:
        return {k: v for k, v in asdict(self).items() if k != "subreddit"} | {"subreddit": self.subreddit}


@dataclass
class SentimentResult:
    """Full output of a sentiment analysis run."""
    backend: str
    threshold: float
    per_subreddit: dict[str, SubredditSentiment] = field(default_factory=dict)
    all_threads: list[ThreadSentiment] = field(default_factory=list)
    total_analyzed: int = 0

    def summary(self) -> str:
        """Return a compact human-readable summary."""
        lines = [
            f"{'=' * 56}",
            f"📊 Sentiment Analysis Summary (backend={self.backend} threshold={self.threshold})",
            f"{'=' * 56}",
            f"Total threads analyzed: {self.total_analyzed}",
            "",
        ]

        total_pos = sum(s.positive_count for s in self.per_subreddit.values())
        total_neu = sum(s.neutral_count for s in self.per_subreddit.values())
        total_neg = sum(s.negative_count for s in self.per_subreddit.values())
        all_scores = [t.score for t in self.all_threads]

        lines.append(f"  Overall: positive={total_pos}, neutral={total_neu}, negative={total_neg}")
        if all_scores:
            overall_mean = statistics.mean(all_scores)
            lines.append(f"  Global mean score: {overall_mean:+.4f}")
        lines.append("")

        # Per-subreddit breakdown
        for sub, stats in sorted(self.per_subreddit.items(), key=lambda x: -x[1].mean_score):
            lines.append(
                f"  r/{sub:<25} mean={stats.mean_score:+.4f}  "
                f"P={stats.positive_count:>3} N={stats.neutral_count:>3} M={stats.negative_count:>3}"
            )

        lines.append("")
        return "\n".join(lines)

    def to_dict(self) -> dict:
        return {
            "backend": self.backend,
            "threshold": self.threshold,
            "total_analyzed": self.total_analyzed,
            "per_subreddit": {k: v.to_dict() for k, v in self.per_subreddit.items()},
            "threads": [t.to_dict() for t in self.all_threads],
        }


# ─────────────────────── VADER Backend (lexicon-based) ───────────────────

class _VADERBackend:
    """Sentiment analysis using NLTK's VADER sentiment lexicon."""

    def __init__(self, threshold: float = 0.1, model_name: str | None = None):
        self.threshold = threshold
        self._initialized = False

    def _ensure_vader(self):
        if not self._initialized:
            try:
                from nltk.sentiment.vader import SentimentIntensityAnalyzer
                import nltk

                nltk.download("vader_lexicon", quiet=True)
                self.vader = SentimentIntensityAnalyzer()
                self._initialized = True
            except Exception as exc:
                raise RuntimeError(
                    "VADER analysis failed. Ensure NLTK data is available:\n"
                    "    python -m nltk.downloader vader_lexicon"
                ) from exc

    def analyze_thread(self, thread: dict) -> ThreadSentiment:
        """Analyze a single thread with VADER."""
        self._ensure_vader()

        text = (thread.get("title", "") or "") + " " + ((thread.get("body") or "")[:500])
        text = re.sub(r"http\S+|www\.\S+", "", text).strip()

        if not text:
            text = "(empty)"

        compound = self.vader.polarity_scores(text)["compound"]  # -1.0 to +1.0

        label = self._score_to_label(compound, self.threshold)

        return ThreadSentiment(
            thread_id=thread.get("id", "unknown"),
            subreddit=thread.get("subreddit", "unknown"),
            label=label,
            score=round(compound, 6),
            title=self._sanitize(thread.get("title", "")),
            body_preview=((thread.get("body") or "")[:80]).strip(),
        )

    @staticmethod
    def _score_to_label(score: float, threshold: float = 0.1) -> SentimentLabel:
        if score > threshold:
            return SentimentLabel.POSITIVE
        elif score < -threshold:
            return SentimentLabel.NEGATIVE
        else:
            return SentimentLabel.NEUTRAL

    @staticmethod
    def _sanitize(text: str) -> str:
        if not text:
            return ""
        return re.sub(r"http\S+|www\.\S+", "URL", text[:120]).strip()


# ─────────────── Transformer Backend (HuggingFace) ─────────────────────

class _TransformersBackend:
    """Sentiment analysis using HuggingFace Transformers pipeline."""

    def __init__(self, threshold: float = 0.1, model_name: str | None = None):
        self.threshold = threshold
        self.model_name = model_name or "distilbert-base-uncased-finetuned-sst-2-english"
        self._pipeline = None
        self._initialized = False

    def _ensure_pipeline(self):
        if self._initialized:
            return True
        try:
            # NOTE: transformers + torch must be installed
            from transformers import pipeline

            print(f"  [SentimentPipeline] Loading {self.model_name} ...")
            self._pipeline = pipeline(
                "sentiment-analysis",
                model=self.model_name,
                tokenizer=self.model_name,
                device=0 if self._has_gpu() else -1,  # GPU if available, else CPU
            )
            self._initialized = True
            print(f"  [SentimentPipeline] ✅ Model loaded ({'GPU' if self._has_gpu() else 'CPU'}).")
            return True
        except ImportError as exc:
            raise ImportError(
                "HuggingFace transformers not installed. Install with:\n"
                "    pip install transformers torch sentencepiece\n"
                f"\nOriginal error: {exc}"
            ) from exc

    @staticmethod
    def _has_gpu() -> bool:
        try:
            import torch  # type: ignore[import-untyped]
            return torch.cuda.is_available() or (
                hasattr(torch.backends, "mps") and torch.backends.mps.is_available()
            )
        except (ImportError, AttributeError):
            return False

    def analyze_thread(self, thread: dict) -> ThreadSentiment:
        """Analyze a single thread with transformer model."""
        loaded = self._ensure_pipeline()
        if not loaded or self._pipeline is None:
            # Return neutral fallback
            return ThreadSentiment(
                thread_id=thread.get("id", "unknown"),
                subreddit=thread.get("subreddit", "unknown"),
                label=SentimentLabel.NEUTRAL,
                score=0.0,
            )

        text = (thread.get("title", "") or "") + ". " + (((thread.get("body") or "")[:200]) or "")
        text = re.sub(r"http\S+|www\.\S+", "", text).strip()

        if not text:
            text = "(empty)"

        result = self._pipeline(text, truncation=True, max_length=512)[0]
        label_raw = result.get("label", "NEUTRAL")
        score_val = float(result.get("score", 0.5))

        # distilbert-sst outputs POSITIVE/NEGATIVE with a confidence score.
        # Convert: positive → +confidence, negative → -conf_score, else neutral.
        if label_raw == "POSITIVE":
            compound = score_val
            label = SentimentLabel.POSITIVE
        elif label_raw == "NEGATIVE":
            compound = -score_val
            label = SentimentLabel.NEGATIVE
        else:
            compound = 0.0
            label = SentimentLabel.NEUTRAL

        return ThreadSentiment(
            thread_id=thread.get("id", "unknown"),
            subreddit=thread.get("subreddit", "unknown"),
            label=label,
            score=round(compound, 6),
            title=self._sanitize(thread.get("title", "")),
            body_preview=((thread.get("body") or "")[:80]).strip(),
        )

    @staticmethod
    def _sanitize(text: str) -> str:
        if not text:
            return ""
        return re.sub(r"http\S+|www\.\S+", "URL", text[:120]).strip()


# ─────────────────────── Main Pipeline class ───────────────────────────

class SentimentPipeline:
    """Unified sentiment analysis pipeline with VADER and Transformer backends.

    Args:
        backend:         "vader" (lexicon, fast) or "transformers" (contextual, accurate).
        threshold:       Minimum absolute score for positive/negative classification.
                         Sentiments in [-threshold, +threshold] are labeled NEUTRAL.
        model_name:      (Transformers only) HF model name for sentiment analysis.

    Attributes:
        result: Latest SentimentResult after calling analyze().
    """

    backends_map = {
        "vader": _VADERBackend,
        "transformers": _TransformersBackend,
    }

    def __init__(self, backend: str = "vader", threshold: float = 0.1, model_name: str | None = None):
        if backend not in self.backends_map:
            raise ValueError(
                f"Unknown backend '{backend}'. Choices: {list(self.backends_map.keys())}"
            )
        self.backend_name = backend
        self.threshold = threshold
        self._backend = self.backends_map[backend](threshold, model_name=model_name)

    def analyze(
        self,
        threads: list[dict],
        n_subreddits: Optional[int] = None,
        verbose: bool = True,
    ) -> SentimentResult:
        """Run sentiment analysis on a list of crawled threads.

        Args:
            threads:     List of dicts with keys 'id', 'subreddit', 'title', optional 'body'.
            n_subreddits: If given and > 0, restrict to top-N subreddits by thread count (default: all).
            verbose:     Print progress indicators.

        Returns:
            SentimentResult with per-thread scores and per-subreddit aggregations.
        """
        if not threads:
            return SentimentResult(backend=self.backend_name, threshold=self.threshold)

        # Determine subreddits to prioritize
        sub_counts: dict[str, int] = {}
        for t in threads:
            s = t.get("subreddit", "unknown")
            sub_counts[s] = sub_counts.get(s, 0) + 1

        if n_subreddits and n_subreddits > 0:
            allowed_subs = set(
                k for k, _ in sorted(sub_counts.items(), key=lambda x: -x[1])[:n_subreddits]
            )
        else:
            allowed_subs = None  # no restriction

        per_sub: dict[str, list[float]] = {}   # subreddit → list of scores
        all_threads: list[ThreadSentiment] = []

        for i, thread in enumerate(threads):
            sub = thread.get("subreddit", "unknown")

            if allowed_subs is not None and sub not in allowed_subs:
                continue

            sentiment = self._backend.analyze_thread(thread)
            all_threads.append(sentiment)
            per_sub.setdefault(sub, []).append(sentiment.score)

            if verbose and (i + 1) % 50 == 0:
                print(f"  [SentimentPipeline] Processed {i+1}/{len(threads)} threads...")

        # Aggregate per-subreddit stats
        aggregated: dict[str, SubredditSentiment] = {}
        for sub, scores in per_sub.items():
            if not scores:
                continue
            n_pos = sum(1 for t in all_threads if t.subreddit == sub and t.label == SentimentLabel.POSITIVE)
            n_neu = sum(1 for t in all_threads if t.subreddit == sub and t.label == SentimentLabel.NEUTRAL)
            n_neg = sum(1 for t in all_threads if t.subreddit == sub and t.label == SentimentLabel.NEGATIVE)
            aggregated[sub] = SubredditSentiment(
                subreddit=sub,
                total_threads=len(scores),
                positive_count=n_pos,
                neutral_count=n_neu,
                negative_count=n_neg,
                mean_score=round(statistics.mean(scores), 6),
                median_score=round(statistics.median(scores), 6),
                std_score=round(statistics.stdev(scores), 6) if len(scores) > 1 else 0.0,
                min_score=min(scores),
                max_score=max(scores),
            )

        return SentimentResult(
            backend=self.backend_name,
            threshold=self.threshold,
            per_subreddit=aggregated,
            all_threads=all_threads,
            total_analyzed=len(all_threads),
        )


# ─────────────────────── Demo / standalone runner ──────────────────────

def run_vader_example():
    """Run a self-contained demo using VADER (no external model download)."""
    print("=" * 60)
    print("   Sentiment Analysis Pipeline - VADER Demo")
    print("=" * 60)

    pipeline = SentimentPipeline(backend="vader", threshold=0.1)

    # Sample data simulating crawled Reddit threads
    threads = [
        {"id": "a1", "subreddit": "python",    "title": "Python is absolutely amazing! Best language ever!",   "body": "Just switched from Java and never looking back."},
        {"id": "a2", "subreddit": "python",    "title": "This tutorial is terrible. Waste of time.",            "body": "Went through it twice. Nothing makes sense."},
        {"id": "a3", "subreddit": "python",    "title": "What are your thoughts on Python 3.12 performance?",   "body": "I heard there are some improvements to the GIL."},
        {"id": "a4", "subreddit": "datascience","title": "Deep learning models are revolutionizing ML pipelines", "body": "Transformers have become essential for NLP tasks like never before."},
        {"id": "a5", "subreddit": "datascience","title": "My model keeps overfitting. Help!",                   "body": "Tried dropout, early stopping, and regularization but validation loss still goes up."},
        {"id": "a6", "subreddit": "datascience","title": "Neutral question about feature scaling methods",           "body": "Is StandardScaler or MinMaxBetter actually? I need to know for better analysis."},
        {"id": "b1", "subreddit": "reactjs",   "title": "React Query is a game changer for data fetching!",       "body": "Caching, deduplication, and refetching on focus — all out of the box."},
        {"id": "b2", "subreddit": "reactjs",   "title": "Why does React keep breaking my app with every update?",  "body": "Had to refactor three times in two months. It is frustrating."},
        {"id": "c1", "subreddit": "machinelearning", "title": "Graph Neural Networks for drug discovery!",        "body": "GCN on molecular graphs shows promising results for property prediction."},
        {"id": "c2", "subreddit": "machinelearning", "title": "Training GPT from scratch is madness but rewarding.","body": "Spent 3 weeks on a cluster. Loss went down but it was painful process."},
    ]

    result = pipeline.analyze(threads, verbose=True)
    print("\n" + result.summary())
    return result


def run_transformers_example():
    """Run a demo using the Transformers backend (requires installation)."""
    print("=" * 60)
    print("   Sentiment Analysis Pipeline - Transformers Demo")
    print("=" * 60)

    try:
        pipeline = SentimentPipeline(backend="transformers", threshold=0.1, model_name=None)
    except Exception as exc:
        print(f"❌ Could not initialize transformers backend:")
        print(f"   {exc}")
        print()
        print("   This is expected if you have not installed HF libraries.")
        print("   To install:  pip install transformers torch sentencepiece")
        return None

    threads = [
        {"id": "t1", "subreddit": "python",     "title": "I love using Python for data analysis! So clean and efficient!", "body": "Pandas + NumPy make everything simple."},
        {"id": "t2", "subreddit": "datascience", "title": "I hate ML conferences. So much hype, no substance.",     "body": "Everyone talks about AI but nobody shows real benchmarks."},
        {"id": "t3", "subreddit": "reactjs",    "title": "Anyone have a good tutorial on Server Components next JS?",  "body": "The documentation is still confusing to me."},
    ]

    result = pipeline.analyze(threads, verbose=True)
    if result:
        print("\n" + result.summary())
    return result


# ──────────────────── Entry point (python -m sentiment.sentiment_pipeline) ───────────────────

if __name__ == "__main__":
    run_vader_example()
    print()

    # Run transforms example if available
    has_transformers = False
    try:
        import transformers  # noqa: F401
        has_transformers = True
    except ImportError:
        pass

    if has_transformers:
        run_transformers_example()
    else:
        print("\n" + "-" * 60)
        print("ℹ️  Transformers backend skipped (not installed).")
        print("   Install with: pip install transformers torch sentencepiece")
