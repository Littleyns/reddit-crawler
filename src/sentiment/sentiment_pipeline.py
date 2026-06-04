"""
Sentiment Analysis Pipeline

Provides unified sentiment scoring for Reddit threads using VADER (pre-installed) 
and an optional Transformer-based analyzer (graceful fallback when transformers isn't available).

Outputs per-thread sentiment scores: compound, pos, neg, neu.
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

# VADER is pre-installed in the venv
from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass
class ThreadSentiment:
    """Sentiment score for a single Reddit thread."""

    url_or_id: str            # thread URL or comment_id
    title_sentiment: float    
    body_sentiment: Optional[float] = None  
    compound_title: float = 0.0          
    compound_body: float = 0.5           
    overall_compound: float = 0.0        
    polarity_label: str = "neutral"      
    

    def to_dict(self) -> Dict[str, Any]:
        return {
            "url_or_id": self.url_or_id,
            "compound_title": round(self.compound_title, 4),
            "compound_body": round(self.compound_body, 4),
            "overall_compound": round(self.overall_compound, 4),
            "polarity_label": self.polarity_label,
        }

    def __str__(self) -> str:
        return (
            f"ThreadSentiment({self.url_or_id[:20]}…) — "
            f"{self.overall_compound:+.4f} ({self.polarity_label})"
        )


@dataclass
class SubredditSentimentSummary:
    """Aggregated sentiment stats for an entire subreddit (or arbitrary collection)."""

    subreddit: str
    thread_count: int
    mean_compound: float
    median_compound: float
    std_compound: float
    positive_ratio: float
    neutral_ratio: float
    negative_ratio: float
    # Raw list so the caller can compute additional stats
    sentiments: List[ThreadSentiment] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "subreddit": self.subreddit,
            "thread_count": self.thread_count,
            "mean_compound": round(self.mean_compound, 4),
            "median_compound": round(self.median_compound, 4),
            "std_compound": round(self.std_compound, 4),
            "positive_ratio": round(self.positive_ratio, 4),
            "neutral_ratio": round(self.neutral_ratio, 4),
            "negative_ratio": round(self.negative_ratio, 4),
        }


# ---------------------------------------------------------------------------
# VADER Analyzer (always available)
# ---------------------------------------------------------------------------

class VaderAnalyzer:
    """Sentiment analyzer backed by VADER.  Works on any English text."""

    def __init__(self):
        self._sia = SentimentIntensityAnalyzer()

    def score(self, text: str) -> Dict[str, float]:
        """Return a dict of {compound, pos, neg, neu}."""
        if not text or not text.strip():
            return {"compound": 0.0, "pos": 0.0, "neg": 0.0, "neu": 1.0}
        raw = self._sia.polarity_scores(text)
        # VADER already returns compound in [-1, 1] and pos/neg/neu in [0, 1]
        return {k: round(v, 4) for k, v in raw.items()}

    def label_from_compound(self, compound: float) -> str:
        if compound >= 0.05:
            return "positive"
        elif compound <= -0.05:
            return "negative"
        return "neutral"


# ---------------------------------------------------------------------------
# Transformer Analyzer (graceful fallback when transformers is missing)
# ---------------------------------------------------------------------------

class TransformerAnalyzer:
    """Lightweight pseudo-transformer sentiment scorer.

    When the ``transformers`` and ``torch`` packages are present this class
    loads a real HuggingFace model (``cardiffnlp/twitter-roberta-base-sentiment``).
    
    Otherwise it falls back to a simple lexicon-based heuristic so that the API
    contract stays identical.
    """

    FALLBACK_MODEL_NAME = "lexicon-fallback"  # marker used in debug output

    def __init__(self, model_name: str = "cardiffnlp/twitter-roberta-base-sentiment"):
        self._model_name = model_name
        self._transformers_available = False
        self._tokenizer = None
        self._model = None
        try:
            # type ignore: we don't want to crash on ImportFailure for disk safety;
            # gracefully fall back at use-time.
            from transformers import AutoTokenizer, AutoModelForSequenceClassification  # type: ignore

            self._transformers_available = True
            logger.info("Loading Transformer model: %s", model_name)
            self._tokenizer = AutoTokenizer.from_pretrained(model_name)
            self._model = AutoModelForSequenceClassification.from_pretrained(model_name)
        except (ImportError, RuntimeError, OSError):
            # Transformers not installed, disk-quota exceeded, CUDA missing — no big deal.
            self._model_name = TransformerAnalyzer.FALLBACK_MODEL_NAME
            logger.warning(
                "transformers unavailable; using lexicon fallback analyzer."
            )

    def score(self, text: str) -> Dict[str, float]:
        if not text or not text.strip():
            return {"compound": 0.0, "pos": 0.5, "neg": 0.0, "neu": 0.5}

        if self._transformers_available and self._tokenizer is not None:
            inputs = self._tokenizer(
                text, truncation=True, max_length=512, return_tensors="pt"
            )
            # type ignore: transformers typing is notoriously messy
            with __import__("torch").no_grad():  # type: ignore
                outputs = self._model(**inputs)  # type: ignore
            scores = outputs.logits[0].softmax(dim=0).tolist()
            labels_order = ["negative", "neutral", "positive"]  # standard order
            return {
                "compound": round(scores[2] - scores[0], 4),
                "pos": round(scores[2], 4),
                "neg": round(scores[0], 4),
                "neu": round(scores[1], 4),
            }

        # Lexicon fallback (same API as VADER without a dedicated analyzer)
        vader = VaderAnalyzer()
        raw = vader.score(text)
        return {
            "compound": raw["compound"],
            "pos": raw["pos"],
            "neg": raw["neg"],
            "neu": raw["neu"],
        }

    def label_from_compound(self, compound: float) -> str:
        """Same API as VaderAnalyzer.label_from_compound()."""
        if compound >= 0.05:
            return "positive"
        elif compound <= -0.05:
            return "negative"
        return "neutral"


# ---------------------------------------------------------------------------
# Pipeline orchestrator
# ---------------------------------------------------------------------------

class SentimentPipeline:
    """High-level pipeline: score a collection of Reddit threads and produce summaries."""

    def __init__(
        self,
        vader: Optional[VaderAnalyzer] = None,
        transformer: Optional[TransformerAnalyzer] = None,
        backend: str = "vader",  # 'vader' | 'transformer' | 'ensemble'
    ):
        self._vader = vader or VaderAnalyzer()
        self._transformer = (
            transformer if transformer is not None else TransformerAnalyzer()
        )
        self._backend = backend

        # Cache so we don't re-score the same text
        self._cache: Dict[str, Dict[str, float]] = {}

    def _get_scores(self, text: str) -> Dict[str, float]:
        """Return sentiment scores using the configured backend."""
        if not text or not text.strip():
            return {"compound": 0.0, "pos": 0.0, "neg": 0.0, "neu": 1.0}

        cached = self._cache.get(text)
        if cached is not None:
            return cached

        if self._backend == "ensemble":
            vader_raw = self._vader.score(text)
            tfmr_scores = self._transformer.score(text)
            combo = {k: round(0.5 * vader_raw[k] + 0.5 * tfmr_scores[k], 4)
                     for k in ("compound", "pos", "neg", "neu")}
        elif self._backend == "transformer":
            combo = self._transformer.score(text)
        else:
            combo = self._vader.score(text)

        self._cache[text] = combo
        return combo

    def _compound(self, scores: Dict[str, float]) -> float:
        return scores.get("compound", 0.0)

    def vader_label_from_compound(self, compound: float) -> str:
        """Determine polarity label from a compound score."""
        if compound >= 0.05:
            return "positive"
        elif compound <= -0.05:
            return "negative"
        return "neutral"

    def score_thread(
        self, title: str, body: Optional[str] = None, url_or_id: str = ""
    ) -> ThreadSentiment:
        """Analyze a single thread and return a ThreadSentiment dataclass."""
        if isinstance(title, dict):
            # Handle legacy call pattern where someone passes the dict directly
            raise TypeError("Pass individual arguments, not a whole dict, to score_thread().")

        title_scores = self._get_scores(title)
        body_scores: Dict[str, float] = {}
        if body and body.strip():
            body_scores = self._get_scores(body)
        else:
            body_scores = {"compound": 0.5, "pos": 0.3, "neg": 0.2, "neu": 0.5}

        title_compound = self._compound(title_scores)
        body_compound = self._compound(body_scores)

        # Weighted average — titles are more indicative of overall tone than comments
        if title and body and body.strip():
            overall = 0.65 * title_compound + 0.35 * body_compound
        else:
            overall = title_compound

        polarity = self.vader_label_from_compound(overall)

        return ThreadSentiment(
            url_or_id=url_or_id,
            title_sentiment=round(title_compound, 4),
            body_sentiment=round(body_compound, 4) if body and body.strip() else None,
            compound_title=round(title_compound, 4),
            compound_body=round(body_compound, 4),
            overall_compound=round(overall, 4),
            polarity_label=polarity,
        )

    def score_threads(
        self, threads: List[Dict[str, str]]
    ) -> List[ThreadSentiment]:
        """Score a list of dicts with keys like '{'title': '...', 'body': '...', 'url': '...'}.'"""
        return [self.score_thread(**t) for t in threads]

    def summary_for(self, sentiments: List[ThreadSentiment], subreddit: str = "") -> SubredditSentimentSummary:
        """Aggregate ThreadSentiment list into a SubredditSentimentSummary."""
        import math
        
        compounds = [s.overall_compound for s in sentiments]
        n = len(compounds)
        if n == 0:
            return SubredditSentimentSummary(
                subreddit=subreddit,
                thread_count=0, mean_compound=0.0, median_compound=0.0, std_compound=0.0,
                positive_ratio=0.0, neutral_ratio=0.0, negative_ratio=0.0, sentiments=[],
            )

        compounds_sorted = sorted(compounds)
        mean_c = sum(compounds) / n
        variance = sum((c - mean_c) ** 2 for c in compounds) / n
        std_c = math.sqrt(variance)
        
        median_idx = n // 2
        if n % 2 == 0 and n >= 2:
            median_c = (compounds_sorted[n // 2 - 1] + compounds_sorted[n // 2]) / 2
        else:
            median_c = compounds_sorted[n // 2]

        pos = sum(1 for c in compounds if self.vader_label_from_compound(c) == "positive")
        neg = sum(1 for c in compounds if self.vader_label_from_compound(c) == "negative")
        
        return SubredditSentimentSummary(
            subreddit=subreddit,
            thread_count=n,
            mean_compound=round(mean_c, 4),
            median_compound=round(median_c, 4),
            std_compound=round(std_c, 4),
            positive_ratio=round(pos / n, 4),
            neutral_ratio=0.0,
            negative_ratio=round(neg / n, 4),
            sentiments=sentiments,
        )

    def score_multiple_subreddits(
        self, data: Dict[str, List[Dict[str, str]]]
    ) -> Dict[str, SubredditSentimentSummary]:
        """Score threads from multiple subreddits and return per-subreddit summaries."""
        return {sname: self.summary_for(thread_sents, subreddit=sname)}
