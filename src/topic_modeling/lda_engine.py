"""
Topic Modeling Module

Rotation Task C: Discover hidden topics in crawled content using Latent Dirichlet Allocation (LDA)
via gensim's MultiCore LDA. Automatically suggests optimal number of topics.

Usage:
    >>> from topic_modeling.lda_engine import LDAModeler
    >>> threads = [
    ...     {"id": "1", "subreddit": "python", "title": "..."},
    ...     {"id": "2", "subreddit": "datascience", "title": "..."},
    ... ]
    >>> modeler = LDAModeler(n_topics=5, random_state=42)
    >>> result = modeler.fit(threads)
    >>> for topic_num, top_terms in result.topics_per_topic.items():
    ...     print(f"Topic {topic_num}: {', '.join(kw for kw, _ in top_terms)}")

Dependencies: `pip install gensim scipy nltk`
"""

import re
from dataclasses import dataclass, asdict
from typing import Optional
from collections import defaultdict, Counter

import numpy as np
from scipy import stats


@dataclass
class TopicLabel:
    """A labeled topic with its top terms."""
    topic_id: int           # Zero-indexed topic number
    top_terms: list[tuple[str, float]]  # (keyword, weight) pairs
    label: str              # Auto-derived label from top term
    perplexity: float       # Model perplexity on held-out data
    coherence_score: float  # Coherence score (C_v)


@dataclass
class TopicDistribution:
    """Topic distribution for a single document."""
    doc_id: str
    subreddit: str
    topic_probs: list[float]          # P(topic_i | doc)
    dominant_topic: int               # Most probable topic index
    confidence: float                 # Confidence of dominant topic assignment


@dataclass
class LDAResult:
    """Full LDA model output."""
    n_topics_modelled: int
    coherence_score: float
    perplexity: float
    topics_per_topic: dict[int, list[tuple[str, float]]]  # {topic_id: [(term, weight)]}
    document_distributions: list[TopicDistribution]
    topic_subreddit_breakdown: dict[int, dict[str, float]]  # {topic_id: {subreddit: avg_prob}}
    optimal_n_topics: int               # Suggested optimal number of topics


class LDAModeler:
    """Discover latent topics in subreddit content using LDA (gensim).

    Features:
        - Automatic coherence-based model selection across K=2..K_range
        - Per-subreddit topic breakout tables
        - Per-document topic assignments
        - C_v coherence scoring for model quality validation
    """

    def __init__(
        self,
        n_topics: int = 5,
        max_k: int = 10,
        random_state: int = 42,
        passes: int = 20,
        alpha: str | float | None = "auto",
        eta: str | float | None = "auto",
        minimum_probability: float = 0.01,
        per_word_topn: int = 20,
    ):
        """Initialize LDA modeler.

        Args:
            n_topics: Number of topics to model.
            max_k: Maximum K to check for automatic selection (K=2..max_k).
            random_state: Reproducibility seed.
            passes: Gensim LDALatentDirichletAllocation passes.
            alpha: Dirichlet prior on document topic distribution. "auto" = symmetric adaptive.
            eta: Dirichlet prior on topic word distribution. "auto" = symmetric adaptive.
            minimum_probability: Minimum probability threshold for reporting topics in a doc.
            per_word_topn: Number of top words per topic to extract.
        """
        self.n_topics = n_topics
        self.max_k = max_k
        self.random_state = random_state
        self.passes = passes
        self.alpha = alpha
        self.eta = eta
        self.minimum_probability = minimum_probability
        self.per_word_topn = per_word_topn

        # Imports happen after init to keep startup fast when LDAModeler not instantiated
        self._lda_model = None
        self._dictionary = None
        self._corpus = None
        self._id2word = None

    # ─────────────────────────────────────────────────╸ Preprocessing ─────────────────────────────────────────────────

    @staticmethod
    def _tokenize(text: str) -> list[str]:
        """Simple tokenizer: lowercase, alphabetic tokens of 3+ chars."""
        if not text:
            return []
        lowered = text.lower()
        tokens = re.findall(r"[a-z]{3,}", lowered)
        # Common stopwords to filter out
        STOP_WORDS = {
            "this", "that", "these", "those", "what", "which", "who", "how",
            "many", "much", "more", "most", "some", "any", "other", "each",
            "every", "both", "few", "less", "might", "also", "would", "could",
            "should", "will", "can", "been", "have", "has", "had", "being",
            "their", "there", "where", "when", "about", "into", "over", "such",
            "with", "from", "then", "just", "only", "like", "well", "get",
            "want", "make", "know", "use", "one", "two", "good", "need",
        }
        return [t for t in tokens if t not in STOP_WORDS]

    def _build_corpus(
        self, threads: list[dict]
    ) -> tuple[" corpora.Dictionary", list[tuple[int, int]], dict[str, str]]:
        """Build gensim dictionary and corpus from thread data.

        Returns:
            (dictionary, corpus_as_list_of_bow, doc_id_map)
                - dictionary: gensim.corpora.Dictionary
                - corpus: list of (token_id, freq) bags of words
                - doc_id_map: {bow_index: {"id": str, "subreddit": str, "text": str}}
        """
        import gensim

        # Collect all token lists and map doc positions to metadata
        doc_meta: dict[int, dict] = {}  # bow_index -> {id, subreddit, text}
        token_lists: list[list[str]] = []

        for idx, t in enumerate(threads):
            text = (t.get("title", "") or "") + " " + ((t.get("body") or "")[:300])
            tokens = self._tokenize(text)
            if not tokens:
                continue
            bow_idx = len(token_lists)
            token_lists.append(tokens)
            doc_meta[bow_idx] = {
                "id": t.get("id", str(idx)),
                "subreddit": t.get("subreddit", "unknown"),
                "text": text,
            }

        if not token_lists:
            raise ValueError("No valid documents to process for LDA.")

        # Build gensim Dictionary and corpus
        dictionary = gensim.corpora.Dictionary(token_lists)
        # Remove very rare / very common terms for cleaner topics
        dictionary.filter_extremes(no_below=2, no_above=0.85)
        bow_corpus = [dictionary.doc2bow(tokens) for tokens in token_lists]

        return dictionary, bow_corpus, doc_meta

    # ─────────────────────────────────────────────────╸ Coherence computation ─────────────────────────────────────────────────

    def _compute_coherence(
        self, model, corpus: list, doc_meta: dict, texts_raw: list[list[str]]
    ) -> float:
        """Compute C_v coherence score for an LDA model."""
        import gensim

        # Prepare texts for coherence (gensim expects list of lists of tokens)
        dictionary = gensim.corpora.Dictionary(texts_raw)
        dictionary.filter_extremes(no_below=2, no_above=0.85)

        from gensim.models.coherencemodel import CoherenceModel

        cm = CoherenceModel(
            model=model,
            texts=texts_raw,
            dictionary=dictionary,
            coherence="c_v",
        )
        return cm.get_coherence()

    # ─────────────────────────────────────────────────╸ Automatic K selection ─────────────────────────────────────────────────

    def _find_optimal_k(
        self,
        corpus: list,
        doc_meta: dict,
        texts_raw: list[list[str]],
    ) -> tuple[int, float]:
        """Fit LDA models for K=2..max_k and pick the one with highest C_v coherence."""
        import gensim

        coherences = []
        best_k = self.n_topics
        best_c = -np.inf

        print(f"\n🔍 Scanning optimal K range (2..{self.max_k})...")
        for k in range(2, self.max_k + 1):
            model = gensim.models.LdaMulticore(
                corpus=corpus,
                id2word=self._id2word,
                num_topics=k,
                random_state=self.random_state,
                passes=max(self.passes // 3, 5),  # Fewer passes for hyperparameter sweep
                workers=-1,  # Use all cores
            )
            c = self._compute_coherence(model, corpus, doc_meta, texts_raw)
            coherences.append((k, c))
            print(f"  K={k:>2d}  C_v={c:.4f}")

            if c > best_c:
                best_c = c
                best_k = k

        print(f"\n✦ Optimal K: {best_k} (C_v={best_c:.4f})")
        return best_k, best_c

    # ─────────────────────────────────────────────────╸ Main Fitting ─────────────────────────────────────────────────

    def fit(
        self,
        threads: list[dict],
        find_best_k: bool = True,
        verbose: bool = True,
    ) -> LDAResult:
        """Fit LDA model to crawled thread data.

        Args:
            threads: List of thread dicts with 'id', 'subreddit', 'title', optional 'body'.
            find_best_k: If True, automatically determine best K before fitting final model.
            verbose: Print progress information.

        Returns:
            LDAResult with topics, document distributions, and coherence/perplexity metrics.
        """
        import gensim

        if verbose:
            print("=" * 60)
            print("📊 LDA Topic Modeler")
            print("=" * 60)

        # Step 1: Build corpus
        dictionary, bow_corpus, doc_meta = self._build_corpus(threads)
        n_docs = len(bow_corpus)
        n_terms = len(dictionary)

        if verbose:
            print(f"\n📄 Corpus: {n_docs} documents, {n_terms} unique terms")
            print(f"   Vocab sample: {list(dictionary.keys())[:15]}...")

        # Prepare raw token lists for coherence scoring
        self._id2word = dictionary
        self._corpus = bow_corpus
        texts_raw: list[list[str]] = []
        for tokens in (dictionary.doc2bow(t) for t in []):
            pass  # placeholder
        texts_raw = [self._tokenize((t.get("title", "") or "") + " " + ((t.get("body") or "")[:300])) for t in threads if t]

        # Step 2: Determine K
        chosen_k = self.n_topics
        best_coherence = -np.inf
        if find_best_k and len(set(t.get("subreddit", "unknown") for t in threads)) >= 2:
            chosen_k, best_coherence = self._find_optimal_k(bow_corpus, doc_meta, texts_raw)

        # Step 3: Fit final model with chosen K
        if verbose:
            print(f"\n🧠 Fitting LDA ({chosen_k} topics, {self.passes} passes)...")

        self._lda_model = gensim.models.LdaMulticore(
            corpus=bow_corpus,
            id2word=dictionary,
            num_topics=chosen_k,
            random_state=self.random_state,
            passes=self.passes,
            workers=-1,
        )
        coherence = -np.inf
        perplexity = -np.inf

        # Compute coherence & perplexity on full corpus
        try:
            coherence = self._compute_coherence(self._lda_model, bow_corpus, doc_meta, texts_raw)
        except Exception:
            pass  # Coherence may fail on some gensim versions; set to 0
        print(f"\n📊 Model Coherence (C_v): {coherence:.4f}")

        # Perplexity approximation (lower is better perplexity)
        try:
            perplexity = self._lda_model.log_perplexity(bow_corpus)
            if np.isfinite(perplexity):
                perplexity = float(np.exp(perplexity))
        except Exception:
            perplexity = -1.0
        print(f"📈 Perplexity (est): {perplexity:.4f}" if np.isfinite(perplexity) else "📈 Perplexity: N/A")

        # Step 4: Extract topics
        topics_per_topic: dict[int, list[tuple[str, float]]] = {}
        id2word = dictionary.id2token
        for topic_id in range(chosen_k):
            top_words_raw = self._lda_model.show_topic(topic_id, self.per_word_topn)
            # Sort by probability descending (show_topic returns sorted descending already) but format properly
            top_words: list[tuple[str, float]] = [
                (id2word[wid], round(float(prob), 6))
                for wid, prob in top_words_raw
            ]
            topics_per_topic[topic_id] = top_words

        # Step 5: Document-level topic distributions
        doc_distributions: list[TopicDistribution] = []
        topic_subreddit_breakdown: dict[int, dict[str, list[float]]] = defaultdict(
            lambda: defaultdict(list)
        )

        for bow_idx in range(len(bow_corpus)):
            # Get dense topic vector for this document
            doc_topics = self._lda_model.get_document_topics(bow_corpus[bow_idx], minimum_prune=0.0)
            # Normalize to sum to 1
            probs = np.zeros(chosen_k)
            total_weight = 0
            for tid, weight in doc_topics:
                probs[tid] = weight
                total_weight += weight

            if total_weight > 0:
                probs /= total_weight

            dominant_topic = int(np.argmax(probs))
            confidence = float(probs[dominant_topic])

            meta = doc_meta.get(bow_idx, {})
            doc_dist = TopicDistribution(
                doc_id=meta.get("id", f"doc_{bow_idx}"),
                subreddit=meta.get("subreddit", "unknown"),
                topic_probs=[round(float(p), 6) for p in probs],
                dominant_topic=dominant_topic,
                confidence=round(confidence, 4),
            )
            doc_distributions.append(doc_dist)

            # Populate subreddit breakdown accumulator
            sub = doc_dist.subreddit
            for tid in range(chosen_k):
                topic_subreddit_breakdown[tid][sub].append(probs[tid])

        # Average the subreddit probabilities per topic
        topic_subreddit_avg: dict[int, dict[str, float]] = {}
        for tid, subs in topic_subreddit_breakdown.items():
            topic_subreddit_avg[tid] = {
                sub: round(float(np.mean(vals)), 6)
                for sub, vals in subs.items()
            }

        result = LDAResult(
            n_topics_modelled=chosen_k,
            coherence_score=round(float(coherence), 4),
            perplexity=round(float(perplexity), 4) if np.isfinite(perplexity) else -1.0,
            topics_per_topic=topics_per_topic,
            document_distributions=doc_distributions,
            topic_subreddit_breakdown=topic_subreddit_avg,
            optimal_n_topics=chosen_k,
        )

        return result


    # ─────────────────────────────────────────────────╸ Topic Labels (auto-naming) ─────────────────────────────────────────────────

    def _derive_topic_labels(self, topics_per_topic: dict[int, list[tuple[str, float]]]) -> dict[int, str]:
        """Auto-derive a label for each topic from its top term."""
        labels: dict[int, str] = {}
        for tid, words in topics_per_topic.items():
            if words:
                # Use the top 2-3 terms as label
                top_terms = [w[0].title() for w in words[:3]]
                labels[tid] = " / ".join(top_terms)
            else:
                labels[tid] = f"Topic_{tid}"
        return labels

    def _format_topic_breakdown(self, result: LDAResult) -> str:
        """Format a human-readable topic breakdown table."""
        labels = self._derive_topic_labels(result.topics_per_topic)
        lines: list[str] = []
        lines.append("\n📋 Topic Breakdown:")
        lines.append("─" * 80)

        for tid in sorted(result.topics_per_topic.keys()):
            label = labels.get(tid, f"Topic_{tid}")
            top_terms = [f"{term}({w:.3f})" for term, w in result.topics_per_topic[tid][:10]]
            lines.append(f"\n  📌 Topic {tid}: {label}")
            lines.append(f"     Terms: {' • '.join(top_terms)}")

        if result.topic_subreddit_breakdown:
            lines.append("\n\n🔀 Top Topics by Subreddit:")
            for tid in sorted(result.topic_subreddit_breakdown.keys()):
                subs = result.topic_subreddit_breakdown[tid]
                if not subs:
                    continue
                top_sub = max(subs.items(), key=lambda x: x[1])
                label = labels.get(tid, f"T{tid}")
                lines.append(f"  Topic {tid} ({label}): dominated by r/{top_sub[0]} (avg_prob={top_sub[1]:.4f})")

        return "\n".join(lines)


def run_example():
    """Demo: Run LDA topic modeling on sample Reddit-style content."""
    print("=" * 60)
    print("📊 LDA Topic Modeler - Demo")
    print("=" * 60)

    # Sample data (simulating crawled Reddit content across subreddits)
    threads = [
        {"id": "1", "subreddit": "python", "title": "Python type hints best practices for large projects", "body": "What are the best practices for using typeddict and mypy in Python 3.11?"},
        {"id": "2", "subreddit": "python", "title": "Using dataclasses with post init inheritance and decorators", "body": "How to properly inherit from dataclasses and override field defaults?"},
        {"id": "3", "subreddit": "python", "title": "Asyncio performance comparison threads vs processes", "body": "I benchmarked asyncio against concurrent.futures. Results surprising."},
        {"id": "4", "subreddit": "python", "title": "Type hints for generic functions and complex signatures", "body": "Struggling with TypeVar constraints and protocol patterns in mypy"},
        {"id": "5", "subreddit": "datascience", "title": "Feature engineering tips for text classification transformers", "body": "What features work best when using BERT for sentiment analysis?"},
        {"id": "6", "subreddit": "datascience", "title": "Should I use XGBoost or neural networks for tabular data?", "body": "My dataset has mixed categorical and numerical features. What works better?"},
        {"id": "7", "subreddit": "datascience", "title": "Data validation pipelines at scale with Great Expectations and dbt", "body": "How do you handle schema changes in production data pipelines?"},
        {"id": "8", "subreddit": "datascience", "title": "Machine learning model monitoring drift detection in production", "body": "Tools for tracking feature drift and concept drift in deployed ML models"},
        {"id": "9", "subreddit": "machinelearning", "title": "Training transformers on custom datasets with HuggingFace Trainers", "body": "Best practices for fine-tuning BERT and RoBERTa on domain-specific text"},
        {"id": "10", "subreddit": "machinelearning", "title": "Graph neural networks for recommender systems research", "body": "Using GCN and GAT layers for collaborative filtering on sparse interaction matrices"},
        {"id": "11", "subreddit": "machinelearning", "title": "Federated learning privacy differential equations and convergence", "body": "Implementing secure aggregation with local SGD in distributed ML systems"},
        {"id": "12", "subreddit": "reactjs", "title": "React Query vs SWR performance comparison 2024 benchmarks", "body": "I migrated from React Query to SWR and here are my detailed benchmarks on caching and prefetching"},
        {"id": "13", "subreddit": "reactjs", "title": "Server components explained beginners guide next js app router", "body": "A simple comprehensive guide understanding Next.js server components client side rendering streaming"},
        {"id": "14", "subreddit": "reactjs", "title": "State management patterns zustand jotai recoil comparison for large apps", "body": "Which library works best for complex form state with use cases and middleware integrations?"},
    ]

    modeler = LDAModeler(n_topics=3, max_k=5, passes=20)
    result = modeler.fit(threads, find_best_k=True)

    print(modeler._format_topic_breakdown(result))

    # Per-document topic assignments
    dominant_subs: dict[int, list[str]] = defaultdict(list)
    for d in result.document_distributions:
        dominant_subs[d.subreddit].append(d.dominant_topic)

    print("\n🔗 Dominant Topics by Subreddit:")
    for sub, topics in dominant_subs.items():
        counter = Counter(topics)
        top_topic, cnt = counter.most_common(1)[0]
        label = modeler._derive_topic_labels(result.topics_per_topic).get(top_topic, f"T{top_topic}")
        print(f"  r/{sub}: {cnt}/{len(topics)} samples → Topic_{top_topic} ({label})")


if __name__ == "__main__":
    run_example()
