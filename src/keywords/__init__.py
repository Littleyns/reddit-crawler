"""
Keyword / Phrase Extraction Package -- Rotation Task B

Extract top themes per subreddit using two complementary methods:
  A) TF-IDF extraction (vectorizer-based, fast, no ML weights needed)
     from keywords.theme_extractor import TFIDFExtractor
  B) KeyBERT phrase extraction (embedding-based, captures semantic phrases)
     from keywords.phrase_extractor import KeyBERTExtractor

Dependencies: `pip install keybert sentence-transformers scikit-learn numpy`
"""

from .theme_extractor import (
    TFIDFExtractor,
    ThemeResult,
    SubredditThemes,
    TfidfExportFormat,
    TFIDFResult,
)
from .phrase_extractor import (
    KeyBERTExtractor,
    KeywordResult,
    SubredditKeywords,
    KeyBERTResult,
)

__all__ = [
    "TFIDFExtractor",
    "KeyBERTExtractor",
    "ThemeResult",
    "SubredditThemes",
    "KeywordResult",
    "SubredditKeywords",
    "KeyBERTResult",
    "TfidfExportFormat",
]
