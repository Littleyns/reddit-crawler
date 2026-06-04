"""
Topic Modeling Package -- Rotation Task C

Discover latent topics in crawled Reddit content via Latent Dirichlet Allocation (LDA)
using gensim. Automatically selects the optimal number of topics using C_v coherence.

Exported API:
    from topic_modeling import LDAModeler, LdarResult

        modeler = LDAModeler(n_topics=5)
        result = modeler.fit(threads)

Dependencies: `pip install gensim scipy nltk`
First run downloads the sentence-transformer model automatically.
"""

from .lda_engine import LDAModeler, LDAResult as LdarResult
from .lda_engine import TopicLabel, TopicDistribution

__all__ = [
    "LDAModeler",
    "LDAResult",
    "TopicLabel",
    "TopicDistribution",
]
