"""
Latent Dirichlet Allocation (LDA) Topic Modeling Module

Implements a lightweight LDA-style topic discovery pipeline using gensim and scikit-learn.
Discovers hidden thematic topics in crawled Reddit content.

Dependencies already installed: gensim, sklearn, numpy

Authors: Hermes Agent — DS Analyst worker
"""

from __future__ import annotations

import logging
from collections import Counter
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Set, Tuple

import numpy as np
from gensim.corpora import Dictionary
from gensim.models import LdaModel  # type: ignore[import]
from gensim.parsing.preprocessing import STOPWORDS as GENERICTOPWORDS

logger = logging.getLogger(__name__)

_EXTENSION_STOP_WORDS: Set[str] = set(GENERICTOPWORDS) | {
    "reddit", "subreddit", "upvote", "upvotes", "downvote", "downvotes",
    "comment", "comments", "reply", "replies", "thread", "threads",
    "op", "original", "poster", "suggested", "editing", "edit",
    "edited", "aww", "oh", "well", "uh", "um", "haha", "hehe", "lol",
    "lmfao", "lmao", "brb", "irl", "imo", "imho", "tbh", "btw", "nvm",
}


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass 
class TopicResult:
    """A single discovered LDA topic with its top terms and strength."""
    topic_id: int
    top_words: List[Tuple[str, float]]          # [(word, weight)]
    dominant_documents: List[str] = field(default_factory=list)
    overall_strength: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "topic_id": self.topic_id,
            "top_words": [(w, round(wt, 4)) for w, wt in self.top_words],
            "dominant_documents": self.dominant_documents[:10],
            "overall_strength": round(self.overall_strength, 4),
        }


@dataclass
class SubredditTopicSummary:
    """Topics discovered within a subreddit."""
    subreddit: str
    n_topics: int
    topics: List[TopicResult] = field(default_factory=list)
    per_topic_coherence: float = 0.0
    perplexity: Optional[float] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "subreddit": self.subreddit,
            "n_topics": self.n_topics,
            "topics": [t.to_dict() for t in self.topics],
            "per_topic_coherence": round(self.per_topic_coherence, 4),
            "perplexity": round(self.perplexity, 4) if self.perplexity else None,
        }


def _clean_document(raw: str) -> List[str]:
    """Tokenize + lowercase + strip stop words for LDA."""
    import re
    lower = raw.lower()
    tokens = re.findall(r"\b[a-z]{3,}\b", lower)
    return [t for t in tokens if t not in _EXTENSION_STOP_WORDS]


class LdaTopicModel:
    """Latent Dirichlet Allocation topic discovery using gensim. """

    def __init__(self, random_state: int = 42, passes: int = 10):
        self._random_state = random_state
        self._passes = passes
        self._dictionary = Dictionary()
        self._lda_model: Optional[LdaModel] = None  # type: ignore
        self._corpus_bow: List[List[Tuple[int, float]]] = []

    # ------------------------------------------------------------------ g gensim fit ---

    def fit(
        self,
        documents: List[str],
        n_topics: int = 5,
        id2word: Optional[Dictionary] = None,
        num_passes: Optional[int] = None,
        alpha: str = "auto",
        chunksize: int = 128,
    ) -> SubredditTopicSummary:
        """Train LDA on provided documents and return a summary."""
        texts_to_train: List[List[str]] = [_clean_document(d) for d in documents]
        
        valid_indices = [i for i, t in enumerate(texts_to_train) if len(t) > 0]
        if len(valid_indices) < 2:
            logger.warning("Fewer than 2 non-empty docs after cleaning.")
            return SubredditTopicSummary(n_topics=0, topics=[], subreddit="")

        texts_to_train = [texts_to_train[i] for i in valid_indices]
        
        if id2word is not None:
            self._dictionary = id2word
        else:
            self._dictionary = Dictionary(texts_to_train)
            self._dictionary.filter_extremes(no_below=2, no_above=0.5)

        self._corpus_bow = [self._dictionary.doc2bow(t) for t in texts_to_train]
        
        if not self._corpus_bow:
            logger.warning("Empty BoW corpus after filtering.")
            return SubredditTopicSummary(n_topics=0, topics=[], subreddit="")

        num_passes = num_passes or self._passes
        logger.info(
            "LDA training - n_topics=%d, passes=%d, docs=%d",
            n_topics, num_passes, len(self._corpus_bow),
        )

        lda_model = LdaModel(  # type: ignore[misc]
            corpus=self._corpus_bow,
            id2word=self._dictionary,
            num_topics=n_topics,
            random_state=self._random_state,
            passes=num_passes,
            alpha=alpha,
            chunksize=chunksize,
            eval_every=None,
        )
        self._lda_model = lda_model

        # Collect topics
        raw_topics = lda_model.show_topics(num_topics=n_topics, formatted=False)
        topics: List[TopicResult] = []

        for topic_idx_raw, (topic_words_list, log_prob_value) in enumerate(raw_topics):
            top_words = [
                (self._dictionary.get(word_id, f"term_{word_id}"), round(float(wt), 4))
                for word_id, wt in topic_words_list[:15]
            ]
            topics.append(TopicResult(
                topic_id=topic_idx_raw + 1,
                top_words=top_words,
                overall_strength=float(log_prob_value),
            ))

        # Try C_v coherence metric
        coherence = 0.0
        try:
            from gensim.models import CoherenceModel  # type: ignore
            cm = CoherenceModel(
                model=lda_model, texts=texts_to_train[:50],
                dictionary=self._dictionary, coherence="c_v",
            )
            coherence = cm.get_coherence()  # type: ignore
        except ImportError:
            pass

        return SubredditTopicSummary(
            subreddit="train_corpus",
            n_topics=n_topics,
            topics=topics,
            per_topic_coherence=round(coherence, 4),
        )

    # ------------------------------------------------------------- sklearn_fit ----
    
    def fit_sklearn(
        self,
        corpus_tokens: List[List[str]],
        n_topics: int = 5,
    ) -> SubredditTopicSummary:
        """Alternative path using scikit-learn LDA (no gensim dependency)."""
        from sklearn.decomposition import LatentDirichletAllocation as SklearnLDA  # type: ignore
        from sklearn.feature_extraction.text import TfidfVectorizer

        texts = [" ".join(tokens) for tokens in corpus_tokens]
        vec = TfidfVectorizer(
            max_features=500,
            stop_words="english",
            token_pattern=r"(?u)\b[a-z]{3,}\b",
        )
        tfidf_matrix = vec.fit_transform(texts)

        lda_sk = SklearnLDA(
            n_components=n_topics, max_iter=20,
            learning_method="online", random_state=self._random_state,
        )
        topic_word_dense = lda_sk.fit_transform(tfidf_matrix)
        
        feature_names = vec.get_feature_names_out()
        topics: List[TopicResult] = []

        for i in range(n_topics):
            top_word_indices_for_topic = np.argsort(topic_word_dense[:, i])[::-1][:15]
            top_words: List[Tuple[str, float]] = [
                (feature_names[idx], round(float(topic_word_dense[i][idx]), 4))
                for idx in top_word_indices_for_topic
            ]

            dominant_docs = [f"doc_{j}" for j in np.argsort(  # type: ignore[arg-type]
                lda_sk.components_[i]
            )[::-1][:5]]
            
            topics.append(TopicResult(
                topic_id=i + 1,
                top_words=top_words,
                dominant_documents=dominant_docs,
                overall_strength=float(np.mean(topic_word_dense[:, i])),
            ))

        ppl = float(lda_sk.perplexity_(tfidf_matrix)) if hasattr(lda_sk, "perplexity_") and lda_sk.perplexity_ is not None else None  # type: ignore

        return SubredditTopicSummary(
            subreddit="custom",
            n_topics=n_topics,
            topics=topics,
            per_topic_coherence=round(float(ppl) if ppl else 0.0, 4),
            perplexity=ppl,
        )

    # ---- accessors -------------------------------------------------

    @property
    def topic_word_distribution(self) -> np.ndarray:
        """Access the word-topic distribution for post-hoc analysis."""
        if self._lda_model is None:
            raise RuntimeError("Call fit() first.")
        # Each row = a topic, each col = a vocab entry.
        n_topics = self._lda_model.num_topics  # type: ignore[attr-defined]
        n_terms = len(self._dictionary)
        dist = np.zeros((n_topics, n_terms))
        for i in range(n_topics):
            for wordid, weight in self._lda_model.state.sufficient_stats["topics"].items():
                if isinstance(wordid, int):
                    dis[i][wordid] = weight
        return dist

    # ---- persistence -----------------------------------------------

    def save(self, path: str) -> None:
        """Persist the Gensim LDA model to disk."""
        if self._lda_model is None:
            raise RuntimeError("No model saved: call fit() first.")
        self._dictionary.save(path + "_dict.dict")
        self._lda_model.save(path + "_lda.model")

    @staticmethod
    def from_disk(dict_path: str, lda_path: str) -> "LdaTopicModel":
        """Restore an LDA model and its dictionary from disk."""
        instance = LdaTopicModel()
        instance._dictionary = Dictionary.load(dict_path)
        instance._lda_model = LdaModel.load(lda_path)  # type: ignore[misc]
        return instance

    # ---- cross-reddit comparison ----------------------------------------# -- topic_overlap -----------------------

    def topic_overlap(
        self, other: "LdaTopicModel", k_words: int = 10
    ) -> Dict[int, float]:
        """Jaccard overlap between this model's topics and another."""
        if self._lda_model is None or other._lda_model is None:
            raise RuntimeError("Both models must be trained to compare.")

        overlaps: Dict[int, float] = {}
        for i in range(self._lda_model.num_topics):  # type: ignore[attr-defined]
            own_words = set(w for w in self lda_model.show_topic(i, topn=k_words))  # type: ignore
            best_overlap = 0.0
            for j in range(other._lda_model.num_topics):  # type: ignore[attr-defined]
                other_words = set(
                    w for w in other._lda_model.show_topic(j, topn=k_words)  # type: ignore]
                )
                union_size = len(own_words | other_words) if own_words or other_words else 1
                overlap = len(own_words & other_words) / max(union_size, 1)
                if overlap > best_overlap:
                    best_overlap = overlap
            overlaps[i + 1] = round(best_overlap, 4)

        return overlap
