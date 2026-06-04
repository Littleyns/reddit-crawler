"""
KeyBERT Phrase Extractor -- Rotation Task B Part 2

Extract top semantic phrases per subreddit using KeyBERT (sentence-transformer
embeddings). Catches themes that surface-level TF-IDF misses by measuring
distributional similarity between documents and candidate terms.

Usage:
    from keywords.phrase_extractor import KeyBERTExtractor, KeywordResult

    extractor = KeyBERTExtractor(model="all-MiniLM-L6-v2", top_k=15)
    result = extractor.extract(threads)
    print(result.global_keywords[0].phrase)

Dependencies: pip install keybert sentence-transformers
First run downloads the sentence-transformer model (~8 MB cached in
~/.cache/huggingface).
"""

from dataclasses import dataclass, asdict
from typing import Optional
import re


# --------------------------------------------------------------------------- #
#  Data classes                                                               #
# --------------------------------------------------------------------------- #

@dataclass
class KeywordResult:
    """Single keyword/phrase extracted by KeyBERT."""
    phrase: str         # The extracted n-gram / keyphrase
    score: float        # Cosine similarity / relevance score [0, 1]

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class SubredditKeywords:
    """Keywords for a single subreddit."""
    subreddit: str
    keywords: list[KeywordResult]

    def to_dict(self) -> dict:
        return {
            "subreddit": self.subreddit,
            "keywords": [kw.to_dict() for kw in self.keywords],
        }


@dataclass
class KeyBERTResult:
    """Full output of a KeyBERT keyword extraction run."""
    per_subreddit: dict[str, SubredditKeywords]
    global_keywords: list[KeywordResult]
    total_documents: int

    def to_dict(self) -> dict:
        return {
            "per_subreddit": {s: v.to_dict() for s, v in self.per_subreddit.items()},
            "global_keywords": [k.to_dict() for k in self.global_keywords],
            "total_documents": self.total_documents,
        }


# --------------------------------------------------------------------------- #
#  Core extractor                                                             #
# --------------------------------------------------------------------------- #

class KeyBERTExtractor:
    """Extract top semantic phrases per subreddit using KeyBERT.

    First invocation automatically downloads the sentence-transformer model
    from HuggingFace Hub (~80 MB, cached in ~/.cache/huggingface).
    """

    DEFAULT_MODEL = "all-MiniLM-L6-v2"   # ~300 MB, fast on CPU
    MIN_DOCS_THRESHOLD = 3                # Need minimum docs for meaningful extraction

    def __init__(self, model_name: str = DEFAULT_MODEL, top_k: int = 15):
        self.model_name = model_name
        self.top_k = top_k
        self._kw_model: Optional[object] = None   # Lazy; set by _ensure_model_loaded()

    # ----- helpers ----------------------------------------------------------

    @staticmethod
    def _clean_text(text: str) -> str:
        """Lowercase, strip URLs / punctuation, collapse whitespace."""
        if not text:
            return ""
        text = text.lower()
        text = re.sub(r"http\S+|www\.\S+", "", text)
        text = re.sub(r"[^\w\s\-]", " ", text)
        text = re.sub(r"\s{2,}", " ", text).strip()
        return text

    def _ensure_model_loaded(self) -> bool:
        """Lazily load the KeyBERT / sentence-transformer model."""
        if self._kw_model is not None:
            return True
        try:
            from keybert import KeyBERT
            print(f"  [KeyBERT] Loading model '{self.model_name}' ...")
            self._kw_model = KeyBERT(model=self.model_name)
            print("  [KeyBERT] Model loaded OK.")
            return True
        except ImportError as exc:
            raise ImportError(
                "keybert and sentence-transformers are required.\n"
                "    pip install keybert sentence-transformers"
            ) from exc

    # ----- public API -------------------------------------------------------

    def extract(
        self,
        threads: list[dict],
        n_subreddits: Optional[int] = None,
    ) -> KeyBERTResult:
        """Extract top KeyBERT keywords from crawled subreddit content.

        Args:
            threads:      List of dicts with 'id', 'subreddit', 'title', optional 'body'.
            n_subreddits: If given, restrict analysis to the top-N subreddits by doc count.
                          Default (None) means all subreddits present in data.

        Returns:
            KeyBERTResult containing per-subreddit and global keyword rankings.
        """
        ok = self._ensure_model_loaded()
        if not ok:
            return KeyBERTResult(
                per_subreddit={}, global_keywords=[], total_documents=0,
            )

        # ---- Step 1 – group docs by subreddit --------------------------------
        sub_docs: dict[str, list[str]] = {}

        for thread in threads:
            title = self._clean_text(thread.get("title", "") or "")
            if not title:
                continue

            body_part = self._clean_text((thread.get("body") or "")[:200])
            combined = title
            if body_part:
                combined += " " + body_part

            subreddit = thread.get("subreddit", "unknown")
            sub_docs.setdefault(subreddit, []).append(combined)

        # ---- Step 2 – pick which subreddits to analyse -----------------------
        all_subs = sorted(sub_docs.keys(), key=lambda s: len(sub_docs[s]), reverse=True)
        selected = all_subs[:n_subreddits] if n_subreddits is not None else all_subs

        per_subreddit: dict[str, SubredditKeywords] = {}
        combined_all: list[str] = []

        for sub in selected:
            docs = [d for d in sub_docs[sub] if len(d.strip()) >= 6]

            if len(docs) < self.MIN_DOCS_THRESHOLD:
                print(
                    f"  [KeyBERT] Skipped r/{sub}: only {len(docs)} valid docs "
                    f"(need >= {self.MIN_DOCS_THRESHOLD})",
                )
                continue

            # Run KeyBERT on the subreddit's documents.
            keywords_raw = self._kw_model.extract_keywords(     # type: ignore[union-attr]
                docs,
                keyphrase_ngram_range=(1, 2),
                stop_words="english",
                top_n=self.top_k,
            )
            keywords = [KeywordResult(phrase=p, score=float(round(s, 4))) for p, s in keywords_raw]

            per_subreddit[sub] = SubredditKeywords(subreddit=sub, keywords=keywords)
            combined_all.extend(docs)

        # ---- Step 3 – global keywords (unique doc set capped at 500 docs) ---
        global_keywords: list[KeywordResult] = []
        if len(combined_all) >= self.MIN_DOCS_THRESHOLD:
            unique_global = list(dict.fromkeys(combined_all[:500]))
            kw_global_raw = self._kw_model.extract_keywords(  # type: ignore[union-attr]
                unique_global,
                keyphrase_ngram_range=(1, 2),
                stop_words="english",
                top_n=self.top_k,
            )
            global_keywords = [KeywordResult(phrase=p, score=float(round(s, 4))) for p, s in kw_global_raw]

        total_docs = sum(len(v) for v in sub_docs.values())

        return KeyBERTResult(
            per_subreddit=per_subreddit,
            global_keywords=global_keywords,
            total_documents=total_docs,
        )

    def extract_thread_keywords(
        self,
        threads: list[dict],
        n_max_docs_per_sub: int = 50,
    ) -> dict[str, list[tuple[str, list[tuple[str, float]]]]]:
        """Per-thread keyword extraction: one keyphrase list per thread.

        Returns {subreddit: [(thread_id, [(keyword, score), ...]), ...]}
        Useful for highlighting the most distinctive phrases in each post.
        """
        ok = self._ensure_model_loaded()
        if not ok:
            return {}

        sub_threads: dict[str, list[dict]] = {}
        for t in threads:
            title = self._clean_text(t.get("title", "") or "")
            body_part = self._clean_text((t.get("body") or "")[:200])
            if not title or len(title) < 6:
                continue

            bid = t.get("id", "unknown")
            subreddit = t.get("subreddit", "unknown")
            sub_threads.setdefault(subreddit, []).append({
                "id": bid,
                "texts_list": [title] + ([body_part] if body_part else []),
            })

        output: dict[str, list[tuple[str, list[tuple[str, float]]]]] = {}
        for sub_name, thread_list in sub_threads.items():
            sub_result: list[tuple[str, list[tuple[str, float]]]] = []
            for item in thread_list[: n_max_docs_per_sub]:
                bid = item["id"]
                texts_list = [txt.strip() for txt in item["texts_list"] if txt.strip()]
                if not texts_list:
                    continue

                # KeyBERT per-document keywords.
                kw_list = self._kw_model.extract_keywords(  # type: ignore[union-attr]
                    texts_list[0], keyphrase_ngram_range=(1, 2), stop_words="english", top_n=8
                )
                sub_result.append((bid, kw_list))
            output[sub_name] = sub_result

        return output


# --------------------------------------------------------------------------- #
#  Demo                                                                       #
# --------------------------------------------------------------------------- #

def run_example() -> Optional[KeyBERTResult]:
    """Run a small demo showing global and per-subreddit keywords."""
    print("=" * 60)
    print("   KeyBERT Keyword Extraction - Demo")
    print("=" * 60)

    sample_threads = [
        {
            "id": "1", "subreddit": "python",
            "title": "Python asyncio vs concurrent futures performance.",
            "body": ("Benchmarking async programming patterns in production workloads. "
                     "I compared the speed of event loops against thread pools for IO-bound tasks."),
        },
        {
            "id": "2", "subreddit": "datascience",
            "title": "Feature engineering tips text classification transformer.",
        },
        {
            "id": "3", "subreddit": "reactjs",
            "title": "React query vs SWR performance comparison 2024 benchmarks.",
        },
        {
            "id": "4", "subreddit": "datascience",
            "title": "Machine learning model monitoring drift detection in production.",
        },
        {
            "id": "5", "subreddit": "machinelearning",
            "title": "Fine-tuning transformers Hugging Face Trainers custom datasets bert roberta.",
        },
    ]

    extractor = KeyBERTExtractor(top_k=10)
    ok = extractor._ensure_model_loaded()
    if not ok:
        print("KeyBERT models are not installed.")
        print("Install with: pip install keybert sentence-transformers")
        return None

    result = extractor.extract(sample_threads, n_subreddits=3)

    # Global keywords.
    print("\nGlobal Top Keywords\n" + "-" * 40)
    for i, kw in enumerate(result.global_keywords[:8], start=1):
        bar = "\u2588" * min(int(kw.score * 30), 30)
        pad = " " * (30 - len(bar))
        print(f"  {i:>2d}. {kw.phrase:<35} score={kw.score:.4f} {bar}{pad}")

    # Per-subreddit keywords.
    print("\n\nPer-Subreddit Keywords\n" + "-" * 40)
    for sub_name, sdata in result.per_subreddit.items():
        print(f"\n  r/{sub_name}\n     " + "." * (len(sub_name) + 4))
        for kw in sdata.keywords[:8]:
            marker = "\u2713" if kw.score > 0.5 else ("\u25cb" if kw.score > 0.3 else " \u2717 ")
            print(f"     {marker:>4}  {kw.phrase:<35} score={kw.score:.4f}")

    return result


if __name__ == "__main__":
    run_example()
