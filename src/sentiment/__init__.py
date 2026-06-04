"""
Sentiment Analysis Package -- Rotation Task A

Analyze sentiment of crawled Reddit threads using dual backends:
  A) VADER (lexicon-based, no model download, instant)
     from sentiment.sentiment_pipeline import SentimentPipeline
     result = SentimentPipeline().analyze(threads, backend="vader")

  B) HuggingFace Transformers (contextual embeddings, higher accuracy)
     from sentiment.sentiment_pipeline import SentimentPipeline
     result = SentimentPipeline().analyze(threads, backend="transformers")

Both backends output per-thread sentiment scores aggregated by subreddit.

Dependencies:
    VADER mode:  pip install vaderSentiment nltk
    Transformer mode: pip install transformers torch sentencepiece
"""

from .sentiment_pipeline import (
    SentimentPipeline,
    SentimentResult,
    ThreadSentiment,
    SubredditSentiment,
)

__all__ = [
    "SentimentPipeline",
    "SentimentResult",
    "ThreadSentiment",
    "SubredditSentiment",
]
