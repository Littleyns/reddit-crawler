"""
Keyword & Theme Extraction Module

Implements:
  - TF-IDF based keyword extraction (scikit-learn)
  - KeyBERT-style semantic keyword extraction using available embeddings
  - Co-occurrence phrase mining for Reddit-specific multi-word phrases
  - Per-subreddit theme summarization
  
All deps are pre-installed in .venv: scikit-learn, numpy, nltk
"""

from __future__ import annotations

import logging
import math
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set, Tuple

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass
class KeywordResult:
    """A single keyword with its TF-IDF score and context."""

    keyword: str
    score: float             # TF-IDF or KeyBERT-style relevance score
    document_count: int = 1   # in how many documents does this appear?
    total_frequency: int = 0  # total occurrences across all docs

    def to_dict(self) -> Dict:
        return {
            "keyword": self.keyword,
            "score": round(self.score, 6),
            "document_count": self.document_count,
            "total_frequency": self.total_frequency,
        }

    def __lt__(self, other: object) -> bool:
        if not isinstance(other, KeywordResult):
            return NotImplemented
        return self.score < other.score


@dataclass
class PhraseResult:
    """Multi-word phrase extracted via co-occurrence analysis."""

    phrase: str
    score: float
    context_examples: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict:
        return {
            "phrase": self.phrase,
            "score": round(self.score, 6),
            "context_examples": self.context_examples[:3],
        }


@dataclass
class SubredditThemeSummary:
    """Top themes for a single subreddit."""

    subreddit: str
    top_keywords: List[KeywordResult] = field(default_factory=list)
    top_phrases: List[PhraseResult] = field(default_factory=list)
    theme_clusters: Dict[str, List[KeywordResult]] = field(default_factory=dict)

    def to_dict(self) -> Dict:
        return {
            "subreddit": self.subreddit,
            "top_keywords": [k.to_dict() for k in self.top_keywords[:20]],
            "top_phrases": [p.to_dict() for p in self.top_phrases[:10]],
            "theme_clusters": {
                cluster: [k.keyword for k in keywords]
                for cluster, keywords in self.theme_clusters.items()
            },
        }


# ---------------------------------------------------------------------------
# TF-IDF Keyword Extractor
# ---------------------------------------------------------------------------

class TfidfKeyExtractor:
    """Extract top keywords from document collections using TF-IDF."""

    # Common English stop words (superset of NLTK's basic set)
    DEFAULT_STOP_WORDS = {
        "the", "a", "an", "and", "or", "but", "if", "in", "on", "at",
        "to", "for", "of", "with", "by", "from", "as", "is", "was", "are",
        "were", "been", "be", "have", "has", "had", "do", "does", "did",
        "will", "would", "could", "should", "may", "might", "shall", "can",
        "this", "that", "these", "those", "it", "its", "i", "me", "my",
        "we", "our", "you", "your", "he", "him", "his", "she", "her", "they",
        "them", "their", "them", "there", "here", "when", "where", "how",
        "what", "which", "who", "whom", "not", "no", "nor", "so", "yet",
        "being", "having", "doing", "just", "about", "above", "after", "again",
        "all", "any", "both", "each", "few", "more", "most", "other", "some",
        "such", "than", "too", "very", "s", "t", "m", "o", "d", "ll", "ve",
        "re", "n't", "won't", "don't", "didn't", "doesn't", "couldn't",
        "shouldn't", "wouldn't", "mustn't", "needn't", "aren't", "isn't",
    }

    def __init__(
        self,
        max_features: int = 1000,
        min_df: int = 2,
        max_df: float = 0.95,
        ngram_range: Tuple[int, int] = (1, 2),
        stop_words: Optional[Set[str]] = None,
    ):
        self.max_features = max_features
        self.ngram_range = ngram_range
        self.stop_words = stop_words or self.DEFAULT_STOP_WORDS
        self.vectorizer = TfidfVectorizer(
            max_features=max_features,
            min_df=min_df,
            max_df=max_df,
            ngram_range=ngram_range,
            stop_words=list(self.stop_words),
            token_pattern=r"(?u)\b\w[\w'-]*\b",  # allow hyphens and apostrophes in words
        )

    def fit_transform_documents(self, documents: List[str]) -> np.ndarray:
        """Build the TF-IDF matrix from raw document list."""
        return self.vectorizer.fit_transform(documents)

    def get_top_keywords(
        self, tfidf_matrix: np.ndarray, n: int = 20, doc_names: Optional[List[str]] = None
    ) -> List[KeywordResult]:
        """Extract top N keywords from a TF-IDF matrix."""
        feature_names = self.vectorizer.get_feature_names_out()
        col_sums = tfidf_matrix.sum(axis=0).A1 if hasattr(tfidf_matrix, 'A1') else np.array(tfidf_matrix.sum(axis=0)).flatten()
        
        candidates: List[KeywordResult] = []
        for i, word in enumerate(feature_names):
            freq = int(col_sums[i])
            if freq > 0:
                candidates.append(
                    KeywordResult(keyword=word, score=float(col_sums[i]), document_count=freq)
                )

        candidates.sort(key=lambda k: k.score, reverse=True)
        return candidates[:n]

    def get_top_docs_for_keyword(
        self, keyword: str, documents: List[str], tfidf_matrix: Optional[np.ndarray] = None
    ) -> List[Tuple[str, float]]:
        """Find the top 5 documents that best match a specific keyword."""
        if tfidf_matrix is None:
            tfidf_matrix = self.vectorizer.fit_transform(documents)

        col_idx = self.vectorizer.vocabulary_.get(keyword)
        if col_idx is None:
            return []

        scores = np.array(tfidf_matrix[:, col_idx].todense()).flatten()
        top_indices = np.argsort(scores)[::-1][:5]

        return [(documents[i], round(float(scores[i]), 4)) for i in top_indices]


# ---------------------------------------------------------------------------
# KeyBERT-style Semantic Keyword Extractor
# ---------------------------------------------------------------------------

class SimpleKeywordExtractor:
    """Lightweight semantic keyword extractor (KeyBERT-inspired).

    Uses word co-occurrence vectors in paragraph-level context as an embedding-like
    representation.  This avoids loading large transformer models while keeping the
    same API contract as KeyBERT for downstream code.
    """

    def __init__(self, window_size: int = 4):
        self.window_size = window_size

    @staticmethod
    def _tokenize(text: str) -> List[str]:
        """Simple word tokenizer — lowercases and splits on whitespace/punctuation."""
        return re.findall(r"\b\w+\b", text.lower())

    def extract_keywords(
        self,
        documents: List[str],
        candidate_filter: Optional[Set[str]] = None,
        top_n: int = 15,
    ) -> List[KeywordResult]:
        """Extract semantic keywords by analyzing word co-occurrence patterns.

        Args:
            documents: List of text documents (titles + bodies).
            candidate_filter: Optional set of stop words to exclude.
            top_n: Number of keywords to return.

        Returns:
            Sorted list of KeywordResult objects.
        """
        # Build co-occurrence counts from the entire corpus
        all_words: Counter = Counter()
        doc_word_counts: Dict[str, int] = {}  # word → {doc_id, count}
        
        for doc in documents:
            words = self._tokenize(doc)
            for word in set(words):
                if candidate_filter is not None and word.lower() in candidate_filter:
                    continue
                all_words[word] += 1
                if word not in doc_word_counts:
                    doc_word_counts[word] = {"docs": set(), "total_count": 0}
                doc_word_counts[word]["docs"].add(id(doc))
                doc_word_counts[word]["total_count"] += len(words)

        # Build co-occurrence vectors for candidate words
        unique_words = all_words.most_common(500)
        word_index = {w: i for i, (w, _) in enumerate(unique_words)}
        n_words = len(unique_words)
        
        # Context vector: how often each word appears near every other word
        context_matrix = np.zeros((n_words, n_words))

        for doc in documents:
            words = self._tokenize(doc)
            for i, w1 in enumerate(words):
                if w1 not in word_index:
                    continue
                idx1 = word_index[w1]
                # Look at window around this word
                start = max(0, i - self.window_size)
                end = min(len(words), i + self.window_size)
                for j in range(start, end):
                    if i == j:
                        continue
                    w2 = words[j]
                    if w2 not in word_index:
                        continue
                    idx2 = word_index[w2]
                    context_matrix[idx1][idx2] += 1

        # Score candidates by their total co-occurring mass (like a personal PageRank)
        scores = np.zeros(n_words)
        iterations = 10  # simple power iteration for scoring
        for _ in range(iterations):
            new_scores = context_matrix.T @ (context_matrix * scores) + (0.15 / n_words)
            norm = np.linalg.norm(new_scores)
            if norm > 0:
                scores = new_scores / norm

        # Normalize to [0, 1] and filter zero-scores
        max_score = float(np.max(scores))
        results: List[KeywordResult] = []
        for idx, (word, freq) in enumerate(unique_words):
            total_freq = doc_word_counts[word]["total_count"] if word in doc_word_counts else 0
            docs_in = len(doc_word_counts[word]["docs"]) if word in doc_word_counts else 0
            normalized_score = float(scores[idx] / max_score) if max_score > 0 else 0.0
            if normalized_score > 0.01:
                results.append(KeywordResult(
                    keyword=word,
                    score=round(normalized_score, 6),
                    document_count=max(docs_in, 1),
                    total_frequency=total_freq,
                ))

        results.sort(key=lambda k: k.score, reverse=True)
        return results[:top_n]


# ---------------------------------------------------------------------------
# Phrase Extractor (co-occurrence based multi-word phrases)
# ---------------------------------------------------------------------------

class PhraseExtractor:
    """Extract meaningful multi-word phrases from Reddit content."""

    # Common single words that break phrase boundaries
    BREAK_WORDS = {
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "shall", "can", "this", "that", "it", "its",
    }

    def extract_phrases(
        self,
        documents: List[str],
        min_length: int = 2,
        min_frequency: int = 3,
        top_n: int = 15,
        phrase_length_range: Tuple[int, int] = (2, 4),
    ) -> List[PhraseResult]:
        """Extract multi-word phrases that co-occur frequently.
        
        Uses a simple sliding-window grammar rule: consecutive words form a candidate
        phrase; filter by frequency and minimum length.
        """
        phrase_counts: Counter = Counter()
        phrase_examples: Dict[str, List[str]] = defaultdict(list)

        for doc in documents:
            sentences = re.split(r'[.\n!?]+', doc)
            for sentence in sentences:
                words = self._tokenize(sentence.lower())
                # Extract phrases of all lengths up to phrase_length_range[1]
                for length in range(min_length, phrase_length_range[1] + 1):
                    for i in range(len(words) - length + 1):
                        phrase_tuple = tuple(words[i:i+length])
                        # Skip if contains too many stop words
                        meaningful = sum(
                            1 for w in phrase_tuple
                            if w.lower() not in self.BREAK_WORDS and len(w) > 2
                        )
                        if meaningful >= length // 2:
                            phrase_key = " ".join(phrase_tuple)
                            phrase_counts[phrase_key] += 1
                            if (phrase_key not in phrase_examples or
                                len(phrase_examples[phrase_key]) < 3):
                                context = sentence.strip()[:80]
                                if context:
                                    phrase_examples[phrase_key].append(context)

        filter_counts = {p: c for p, c in phrase_counts.items() if c >= min_frequency}
        
        max_freq = max(filter_counts.values()) if filter_counts else 1
        
        results = [
            PhraseResult(
                phrase=p,
                score=c / max_freq,
                context_examples=phrase_examples.get(p, [])[:3],
            )
            for p, c in filter_counts.items()
        ]
        results.sort(key=lambda p: p.score, reverse=True)
        return results[:top_n]


# ---------------------------------------------------------------------------
# Theme Clustering (simple k-means on TF-IDF vectors)
# ---------------------------------------------------------------------------

class ThemeClusterer:
    """Group keywords into thematic clusters using simple cosine similarity."""

    def __init__(self, n_clusters: int = 5):
        self.n_clusters = n_clusters

    def cluster(
        self,
        keywords: List[str],
        corpus: List[str],
        n_clusters: Optional[int] = None,
    ) -> Dict[str, List[KeywordResult]]:
        """Cluster keywords into thematic groups based on their co-occurrence patterns.
        
        Uses sklearn KMeans to group similar keywords.
        Falls back to alphabetical sub-grouping if sklearn is minimal.
        """
        if not keywords:
            return {}

        cluster_count = n_clusters or self.n_clusters
        cluster_count = min(cluster_count, len(keywords))

        # Build TF-IDF for the corpus using only the keyword terms
        vectorizer = TfidfVectorizer(
            vocabulary=keywords,
            token_pattern=r"(?u)\b\w[\w'-]*\b",
            max_df=1.0,
            min_df=1,
        )
        try:
            tfidf_matrix = vectorizer.fit_transform(corpus)
        except (ValueError, TypeError):
            return {f"cluster_{i}": [KeywordResult(keyword=k, score=1.0)] for i, k in enumerate(keywords)}

        # Simple KMeans implementation using sklearn if available
        try:
            from sklearn.cluster import KMeans
            
            km = KMeans(n_clusters=cluster_count, random_state=42, n_init=10)
            labels = km.fit_predict(tfidf_matrix)
            
            clusters: Dict[str, List[KeywordResult]] = defaultdict(list)
            for kword, label in zip(keywords, labels):
                clusters[f"cluster_{label}"].append(KeywordResult(keyword=kword, score=1.0))
            
            # Sort each cluster by keyword alphabetically for consistency
            result: Dict[str, List[KeywordResult]] = {}
            for key, kw_list in sorted(clusters.items()):
                kw_list.sort(key=lambda x: x.keyword)
                result[key] = kw_list
            return result

        except ImportError:
            # Fallback: alphabetical grouping
            sorted_keywords = sorted(keywords)
            chunk_size = max(1, len(sorted_keywords) // cluster_count)
            result: Dict[str, List[KeywordResult]] = {}
            for i in range(cluster_count):
                start = i * chunk_size
                end = start + chunk_size if i < cluster_count - 1 else len(sorted_keywords)
                cluster_kw = sorted_keywords[start:end]
                result[f"cluster_{i}"] = [
                    KeywordResult(keyword=kword, score=1.0) for kword in cluster_kw
                ]
            return result


# ---------------------------------------------------------------------------
# Main orchestrator: ThemeExtractor
# ---------------------------------------------------------------------------

class ThemeExtractor:
    """High-level keyword/theme extraction pipeline.
    
    Combines TF-IDF, semantic (KeyBERT-style) keywords, phrase mining, and
    theme clustering into a unified API for Reddit analytics.
    """

    def __init__(
        self,
        tfidf_top_n: int = 20,
        semantic_top_n: int = 15,
        phrase_top_n: int = 15,
        n_clusters: int = 5,
    ):
        self.tf_idf_extractor = TfidfKeyExtractor()
        self.semantic_extractor = SimpleKeywordExtractor()
        self.phrase_extractor = PhraseExtractor()
        self.clusterer = ThemeClusterer(n_clusters=n_clusters)
        self._tfidf_top_n = tfidf_top_n
        self._semantic_top_n = semantic_top_n
        self._phrase_top_n = phrase_top_n

    def extract_from_subreddit(
        self,
        texts: List[str],
        subreddit_name: str = "unnamed",
        n_clusters: Optional[int] = None,
    ) -> SubredditThemeSummary:
        """Run full keyword/phrase/theme extraction on a list of documents (subreddit texts)."""
        # 1. TF-IDF keywords
        tfidf_matrix = self.tf_idf_extractor.fit_transform_documents(texts)
        tfidf_kwargs = self.tf_idf_extractor.get_top_keywords(tfidf_matrix, n=self._tfidf_top_n)

        # 2. Semantic/KeyBERT-style keywords
        semantic_kwargs = self.semantic_extractor.extract_keywords(
            texts,
            candidate_filter=self.tf_idf_extractor.DEFAULT_STOP_WORDS,
            top_n=self._semantic_top_n,
        )

        # 3. Multi-word phrases
        phrases = self.phrase_extractor.extract_phrases(texts, top_n=self._phrase_top_n)

        # 4. Theme clusters
        all_kw_texts = [kw.keyword for kw in tfidf_kwargs]
        clusters = self.clusterer.cluster(all_kw_texts[:50], texts, n_clusters)

        return SubredditThemeSummary(
            subreddit=subreddit_name,
            top_keywords=tfidf_kwargs,
            top_phrases=phrases,
            theme_clusters=clusters,
        )

    def extract_from_multiple_subreddits(
        self, data: Dict[str, List[str]]
    ) -> Dict[str, SubredditThemeSummary]:
        """Extract themes from multiple subreddits at once."""
        return {sn: self.extract_from_subreddit(texts, sn) for sn, texts in data.items()}
