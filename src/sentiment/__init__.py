"""Sentiment analysis module -- Rotation Task A.

Provides VADER and HuggingFace transformer-based sentiment scoring per thread.
"""

from .sentiment_pipeline import (
    SentimentPipeline,
    ThreadSentiment,
    SubredditSentiment,
    SentimentResult,
)

__all__ = [
    "SentimentPipeline",
    "ThreadSentiment",
    "SubredditSentiment",
    "SentimentResult",
]
