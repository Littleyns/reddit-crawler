"""
TF-IDF Theme Extractor -- Rotation Task B Part 1

Extract top themes per subreddit using scikit-learn TfidfVectorizer.
No ML model download required; lightweight and fast.

Usage:
    from keywords.theme_extractor import TFIDFExtractor, TfidfExportFormat

    extractor = TFIDFExtractor(top_k=20)
    result = extractor.extract(threads, n_subreddits=3)

Dependencies: pip install scikit-learn numpy
"""

import re
from dataclasses import dataclass, asdict
from enum import Enum
from typing import Optional
from collections import defaultdict


@dataclass
class ThemeResult:
    """Single theme (n-gram / phrase) returned by TF-IDF extraction."""
    phrase: str
    score: float
    document_count: int


@dataclass
class SubredditThemes:
    """Themes extracted for one subreddit."""
    subreddit: str
    themes: list[ThemeResult]

    def to_dict(self):
        return {
            "subreddit": self.subreddit,
            "themes": [asdict(t) for t in self.themes],
        }


@dataclass
class TFIDFResult:
    """Full output of a TF-IDF theme extraction run."""
    per_subreddit: dict[str, SubredditThemes]
    global_themes: list[ThemeResult]
    total_documents: int

    def to_dict(self):
        return {
            "per_subreddit": {s: v.to_dict() for s, v in self.per_subreddit.items()},
            "global_themes": [asdict(t) for t in self.global_themes],
            "total_documents": self.total_documents,
        }


class TfidfExportFormat(Enum):
    """Supported export formats for TF-IDF data."""
    CSV = "csv"
    JSON = "json"
    TOP_K_PER_SUBREDDIT = "top_k_per_subreddit"
    GLOBAL_BOW = "global_bow"  # bag-of-words


class TFIDFExtractor:
    """Extract top themes per subreddit using scikit-learn TfidfVectorizer.

    Filters out Reddit-specific stop patterns (u/name, r/subreddit, URLs)
    *before* vectorization to avoid them dominating vocabularies.
    Supports unigrams + bigrams by default; configurable up-trigrams and more.
    """

    # Reddit-specific tokens not in NLTK / sklearn English lists but very common below
    REDDIT_EXTRA_STOP = [
        "r", "u", "reddit", "redditors", "upvotes", "downvotes",
        "subreddit", "link", "comment", "comments", "post", "posts",
        "thread", "threads", "submitted", "op", "via", "from",
    ]

    def __init__(
        self,
        top_k: int = 20,
        ngram_range: tuple[int, int] = (1, 2),
        max_features: int = 5000,
        min_df: int = 2,
        max_df: float = 0.95,
    ):
        self.top_k = top_k
        self.ngram_range = ngram_range
        self.max_features = max_features
        self.min_df = min_df
        self.max_df = max_df

        # Build final stop-word list from sklearn + reddit extras
        from sklearn.feature_extraction.text import TfidfVectorizer, ENGLISH_STOP_WORDS

        extra = set()
        for term in self.REDDIT_EXTRA_STOP:
            extra.update(term)

        stop_list = list(set(list(ENGLISH_STOP_WORDS) + list(extra)))

        self.vectorizer = TfidfVectorizer(
            ngram_range=ngram_range,
            max_features=max_features,
            min_df=min_df,
            max_df=max_df,
            stop_words=stop_list,
            token_pattern=r"(?u)\b\w[\w-]*\w\b|\b\w{2,}\b",
        )

    # ------------------------------------------------------------------ #
    #  Public API                                                          #
    # ------------------------------------------------------------------ #

    def extract(
        self,
        threads: list[dict],
        n_subreddits: Optional[int] = None,
    ) -> TFIDFResult:
        """Extract top TF-IDF themes from crawled subreddit content.

        Args:
            threads:  List of dicts with at least 'id', 'subreddit', 'title' keys;
                      optional 'body'.
            n_subreddits:  If given, restrict analysis to the top-N subreddits by doc count.

        Returns:
            TFIDFResult containing per-subreddit and global keyword rankings.
        """
        # ---- Step 1: clean + group by subreddit ---------------------------
        sub_docs: dict[str, list[str]] = defaultdict(list)

        for thread in threads:
            title = self._clean_text(thread.get("title", ""))
            body_part = self._clean_text((thread.get("body") or "")[:200])

            if not title:
                continue

            doc_text = title
            if body_part:
                doc_text += " " + body_part

            sub = thread.get("subreddit", "unknown")
            sub_docs[sub].append(doc_text)

        # ---- Step 2: decide which subreddits to analyze --------------------
        all_subs = sorted(sub_docs.keys(), key=lambda s: len(sub_docs[s]), reverse=True)
        selected = all_subs[:n_subreddit] if n_subreddit else all_subs

        # ---- Step 3: per-subreddit TF-IDF ----------------------------------
        per_subreddit: dict[str, SubredditThemes] = {}
        combined_all: list[str] = []

        for sub in selected:
            docs = sub_docs[sub]
            if len(docs) < self.min_df:
                continue  # not enough evidence to extract from

            themes = self._top_ngrams_from_docs(docs)
            per_subreddit[sub] = SubredditThemes(subreddit=sub, themes=themes)
            combined_all.extend(docs)

        # ---- Step 4: global TF-IDF -----------------------------------------
        if combined_all:
            global_themes = self._top_ngrams_from_docs(list(set(combined_all)))
        else:
            global_themes = []

        return TFIDFResult(
            per_subreddit=per_subreddit,
            global_themes=global_themes,
            total_documents=sum(len(d) for d in sub_docs.values()),
        )

    def get_top_keywords_per_subreddit(
        self,
        articles: list[dict],
        n_subreddits: Optional[int] = None,
        top_k_override: Optional[int] = None,
    ):
        """Convenience wrapper returning keyword lists per subreddit.

        Returns dict mapping subreddit name -> list of (keyword, score) tuples.
        """
        eff_top = top_k_override or self.top_k

        saved_top_k = self.top_k  # restore later
        if top_k_override is not None:
            self.top_k = top_k_override

        result = self.extract(articles, n_subreddits=n_subreddit)

        output_dict = {}
        for sub_, v in result.per_subreddit.items():
            output_dict[sub_] = [(t.phrase, t.score) for t in v.themes[:eff_top]]

        self.top_k = saved_top_k
        return output_dict

    # ------------------------------------------------------------------ #
    #  Internal helpers                                                    #
    # ------------------------------------------------------------------ #

    @staticmethod
    def _clean_text(text: str) -> str:
        """Lowercase, strip URLs / punctuation, collapse spaces."""
        if not text:
            return ""
        cleaned = text.lower()
        cleaned = re.sub(r"http\S+|www\.\S+", "", cleaned)
        cleaned = re.sub(r'[^\w\s-]', ' ', cleaned)
        cleaned = re.sub(r'\s{2,}', ' ', cleaned).strip()
        return cleaned

    def _top_ngrams_from_docs(self, docs: list[str]) -> list[ThemeResult]:
        """Run vectorizer on *docs* and return the top-K n-grams by TF-IDF mean.

        The returned ThemeResults are sorted descending by score.
        """
        bow_matrix = self.vectorizer.fit_transform(docs)
        vocab = list(self.vectorizer.get_feature_names_out())

        # Column-wise mean TF-IDF
        col_means = bow_matrix.mean(axis=0).A1
        doc_freqs = (bow_matrix > 0).sum(axis=0).A1

        themes: list[ThemeResult] = []
        for term, score, freq in zip(vocab, col_means, doc_freqs):
            if freq >= self.min_df:
                themes.append(ThemeResult(phrase=term, score=round(float(score), 6), document_count=int(freq)))

        themes.sort(key=lambda t: -t.score)
        return themes[:self.top_k]


# ------------------------------------------------------------------ #
#  Standalone demo                                                     #
# ------------------------------------------------------------------ #

def run_example():
    """Demo script showcasing TF-IDF extraction."""
    print("=" * 60)
    print("TF-IDF Theme Extraction Demo")
    print("=" * 60)

    sample_threads = [
        {
            "id": "1",
            "subreddit": "python",
            "title": "Best practices for Python type hints in large projects",
            "body": "What is a good way using typed dict and mypy for Python 3.11?",
        },
        {
            "id": "2",
            "subreddit": "datascience",
            "title": "Feature engineering tips XGBoost vs neural network tabular data",
            "body": "Which approach handles mixed categorical features better?",
        },
        {
            "id": "3",
            "subreddit": "datascience",
            "title": "Data validation pipelines Great Expectations dbt schema changes",
            "body": "How do you handle schema evolution in production?",
        },
        {
            "id": "4",
            "subreddit": "reactjs",
            "title": "React Query SWR performance benchmarks 2024 migration guide",
            "body": "Migrating from React Query to SWR and here are my benchmarks.",
        },
        {
            "id": "5",
            "subreddit": "machinelearning",
            "title": "Fine-tuning transformers Huggingface Trainers custom datasets bert roberta",
            "body": "Best practices for fine-tuning BERT and RoBERTa on domain text.",
        },
    ]

    extractor = TFIDFExtractor(top_k=12)
    result = extractor.extract(sample_threads, n_subreddits=3)

    print(f"\nAnalyzed {result.total_documents} documents across "
          f"{len(result.per_subreddit)} subreddits\n")

    # Global themes with a mini bar chart
    print("Global Top Themes\n" + "-" * 40)
    for i, theme in enumerate(result.global_themes[:8], start=1):
        bar = "\u2588" * min(int(theme.score * 30), 30)
        pad = " " * (30 - len(bar))
        print(f"  {i:>2d}. {theme.phrase:<35} score={theme.score:.4f} {bar}{pad}")

    # Per-subreddit themes
    print("\n\nPer-Subreddit Keywords\n" + "-" * 40)
    for sub, s in result.per_subreddit.items():
        print(f"\n  r/{sub}\n" + "     -" * (len(sub) + 4))
        for t in s.themes[:8]:
            marker = "\u2713" if t.score > 0.5 else ("\u25cb" if t.score > 0.3 else " \u2717 ")
            print(f"     {marker:>4} • {t.phrase:<28} {t.score:.4f}")

    return result


if __name__ == "__main__":
    run_example()
