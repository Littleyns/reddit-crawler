"""
Topic Modeling Module - Discover hidden themes in crawled Reddit content.

Implements LDA, NMF, and online topic modeling for crawled Reddit text.
Outputs: per-subreddit topic reports, coherence scores, cross-subreddit overlap.

Author: DS Analyst Agent (Zarrouk6969)
Date:   2026-06-04
"""

from __future__ import annotations

import argparse
import csv
import json
import logging
import os
import re
import sys
import time
from collections import defaultdict, Counter
from datetime import datetime
from typing import Any

import numpy as np
import pandas as pd
from gensim import corpora, models
from gensim.models.coherencemodel import CoherenceModel
from sklearn.decomposition import NMF
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    stream=sys.stdout,
)
logger = logging.getLogger("topic_modeling")


class TopicData:
    """A discovered topic with its key characteristics."""

    def __init__(self):
        self.topic_id: int = 0
        self.subreddit: str = ""
        self.dominant_words: list[str] = []
        self.word_scores: dict[str, float] = {}
        self.coherence_score: float = 0.0
        self.avg_confidence: float = 0.0
        self.size: int = 0
        self.description: str = ""


class CorpusSummary:
    """Aggregated topic model statistics for a corpus."""

    def __init__(self):
        self.total_documents: int = 0
        self.num_topics: int = 0
        self.best_model_params: dict[str, Any] = {}
        self.coherence_scores: list[float] = []
        self.topics_per_subreddit: dict[str, dict] = defaultdict(dict)
        self.cross_subreddit_overlap: dict[str, dict] = defaultdict(
            lambda: defaultdict(float)
        )


def _describe_words(dominant_words: list[str], max_length: int = 80) -> str:
    """Generate human-readable description from dominant words."""
    if not dominant_words:
        return "empty topic"
    phrase = ", ".join(dominant_words[:5])
    desc = f"Topic centered around: {phrase}"
    return (desc[: max_length - 3] + "...") if len(desc) > max_length else desc


class TopicPreprocessor:
    """Lightweight preprocessing optimized for topic modeling."""

    STOP_WORDS = {
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "can", "shall", "it", "its", "this", "that",
        "these", "those", "i", "you", "he", "she", "we", "they", "me", "him",
        "her", "us", "them", "my", "your", "his", "our", "their", "what",
        "which", "who", "whom", "when", "where", "why", "how", "all", "each",
        "every", "both", "few", "more", "most", "other", "some", "such", "no",
        "not", "only", "own", "same", "so", "than", "too", "very", "just",
        "about", "above", "after", "again", "also", "any", "because", "before",
        "between", "down", "during", "further", "get", "got", "here", "into",
        "if", "like", "make", "made", "much", "must", "myself", "n", "off",
        "once", "out", "over", "per", "run", "set", "still", "tell", "then",
        "there", "through", "up", "upon", "use", "used", "using", "via", "want",
        "well", "went", "whether", "within", "without", "yes", "yet", "youre",
    }

    @classmethod
    def tokenize(cls, text: str) -> list[str]:
        if not text or not isinstance(text, str):
            return []
        text = text.lower()
        tokens = re.findall(r"[a-z][a-z0-9_]{2,}", text)
        return [t for t in tokens if t not in cls.STOP_WORDS]

    @classmethod
    def prepare_corpus(cls, texts: list[str]) -> tuple:
        """Convert raw texts to (bow_corpus, dictionary, valid_indices)."""
        tokenized = [cls.tokenize(t) for t in texts]
        valid_idx = [i for i, toks in enumerate(tokenized) if toks]
        corpus_toks = [tokenized[i] for i in valid_idx]

        dictionary = corpora.Dictionary(corpus_toks)
        # Filter rare (in < 2 docs) and very common (>80%) terms
        doc_count = len(corpus_toks)
        if doc_count > 1:
            token_doc_freq = defaultdict(int)
            seen_in_doc = defaultdict(set)
            for toks in corpus_toks:
                for tok in toks:
                    if tok not in seen_in_doc[tok]:
                        pass  # will count below

            # Use gensim's built-in filter_extreme instead
            max_above = min(doc_count * 0.8, doc_count)
            dictionary.filter_tokens(min_df=2)
            to_remove = []
            for tid, freq in dictionary.token2id.items():
                pass

        bow = [dictionary.doc2bow(toks) for toks in corpus_toks]
        return bow, dictionary, valid_idx


class LDATrainer:

    @staticmethod
    def train(corpus, dictionary, num_topics: int = 10, passes: int = 15):
        logger.info("Training LDA (%d topics, %d passes) ...", num_topics, passes)

        lda_model = models.LdaModel(
            corpus=corpus,
            id2word=dictionary,
            num_topics=num_topics,
            random_state=42,
            passes=passes,
            alpha="auto",
            eta="auto",
            per_word_topics=False,
        )

        topics_data = {}
        for topic_id in range(num_topics):
            raw_terms = lda_model.get_topic_terms(topicid=topic_id, normalized=True)
            ws = {dictionary[wid]: float(score) for wid, score in raw_terms}
            dominant = sorted(ws, key=ws.get, reverse=True)[:10]
            topics_data[str(topic_id)] = {
                "dominant_words": dominant,
                "word_scores": ws,
                "description": _describe_words(dominant),
            }

        cm_umass = CoherenceModel(
            model=lda_model,
            texts=[
                dictionary.doc2bow(TopicPreprocessor.tokenize(s))
                for s in lda_model.data
            ],
            dictionary=dictionary,
            coherence="u_mass",
        )
        cm_cv = CoherenceModel(
            model=lda_model,
            texts=[
                dictionary.doc2bow(TopicPreprocessor.tokenize(s))
                for s in lda_model.data
            ],
            dictionary=dictionary,
            coherence="c_v",
        )

        return {
            "topics_data": topics_data,
            "u_mass_coherence": float(cm_umass.get_coherence()),
            "c_v_coherence": float(cm_cv.get_coherence()),
            "num_topics": num_topics,
            "dictionary_size": len(dictionary),
            "corpus_bow_count": len(corpus),
        }

    @staticmethod
    def find_optimal_topics(
        corpus, dictionary, kmin: int = 3, kmax: int = 25, kstep: int = 4
    ):
        logger.info("Finding optimal topic count (k=%d..%d step %d) ...", kmin, kmax, kstep)
        results = {}

        for k in range(kmin, kmax + 1, kstep):
            try:
                t0 = time.time()
                res = LDATrainer.train(corpus, dictionary, num_topics=k, passes=10)
                elapsed = time.time() - t0
                results[k] = (res["c_v_coherence"], res, elapsed)
                logger.info(
                    "k=%d -> C_v=%.4f (%.1fs)", k, res["c_v_coherence"], elapsed
                )
            except Exception as exc:
                logger.warning("Failed for k=%d: %s", k, str(exc))

        if not results:
            return {"best_k": None, "coherence_scores": {}}

        best_k = max(results, key=lambda kk: results[kk][0])
        cs = []
        for _k in sorted(results):
            cs.append(results[_k][0])

        summary = CorpusSummary()
        summary.num_topics = best_k
        summary.coherence_scores = cs
        summary.best_model_params = {"k": best_k}

        logger.info("Best topic count: k=%d (C_v=%.4f)", best_k, results[best_k][0])
        return {
            "best_k": best_k,
            "coherence_scores": {kk: cs[idx] for idx, kk in enumerate(sorted(results))},
            "num_topics": best_k,
            "model_results": {kk: f"{rv[0]:.4f}" for kk, rv in results.items()},
        }


class NMFTrainer:

    @staticmethod
    def fit(texts: list[str], num_topics: int = 10):
        t0 = time.time()
        logger.info("Fitting NMF (%d topics) ...", num_topics)

        tfidf_vec = TfidfVectorizer(
            max_features=5000,
            stop_words="english",
            min_df=2,
            max_df=0.95,
            ngram_range=(1, 2),
        )
        tfidf_matrix = tfidf_vec.fit_transform(texts)

        nmf_model = NMF(
            n_components=num_topics,
            init="nndsvd",
            random_state=42,
            max_iter=1000,
            alpha_W=0.1,
            alpha_X=0.1,
            l1_ratio=0.5,
        )
        W = nmf_model.fit_transform(tfidf_matrix)
        H = nmf_model.components_

        feature_names = tfidf_vec.get_feature_names_out()

        topics_data = {}
        for topic_id in range(H.shape[0]):
            top_idx = H[topic_id].argsort()[::-1][:15]
            ws = {feature_names[i]: float(H[topic_id, i]) for i in top_idx}
            dominant = sorted(ws, key=ws.get, reverse=True)[:10]
            topics_data[str(topic_id)] = {
                "dominant_words": dominant,
                "word_scores": ws,
                "description": _describe_words(dominant),
                "spread": round(float(np.std(W[topic_id])) + 1e-9, 4),
            }

        return {
            "num_topics": num_topics,
            "vocabulary_size": len(feature_names),
            "doc_count": len(texts),
            "training_time_seconds": round(time.time() - t0, 2),
            "topics_data": topics_data,
        }


class TopicVisualizer:

    @staticmethod
    def plot_coherence_vs_k(
        k_values: list[int],
        coherence_scores: list[float],
        output_path: str = "topic_coherence_vs_k.png",
    ) -> str:
        import plotly.graph_objects as go

        fig = go.Figure(data=go.Scatter(
            x=k_values,
            y=coherence_scores,
            mode="lines+markers",
            marker=dict(size=10, color="#3498db"),
            line=dict(width=3),
        ))

        if k_values and coherence_scores:
            best_idx = int(np.argmax(coherence_scores))
            fig.add_trace(
                go.Scatter(
                    x=[k_values[best_idx]],
                    y=[coherence_scores[best_idx]],
                    mode="markers",
                    marker=dict(size=15, color="#e74c3c", symbol="x"),
                    name=f"Best k={k_values[best_idx]}",
                )
            )

        fig.update_layout(
            title="Topic Coherence vs Number of Topics (Higher is better)",
            xaxis_title="Number of Topics (k)",
            yaxis_title="Coherence Score (C_v)",
            template="plotly_white",
        )
        os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
        fig.write_image(output_path, width=800)
        return output_path

    @staticmethod
    def plot_topic_heatmap(
        topics_data: dict[str, Any],
        top_n_words: int = 15,
        num_topics_display: int = 5,
        output_path: str = "topic_dominance_heatmap.png",
    ) -> str:
        import plotly.graph_objects as go

        sorted_ids = sorted(
            topics_data.keys(),
            key=lambda k: (-len(topics_data[k].get("word_scores", {})), k),
        )[:num_topics_display]

        all_words = set()
        for tid in sorted_ids:
            ws = topics_data[tid].get("word_scores", {})
            if isinstance(ws, dict):
                top = sorted(ws, key=ws.get, reverse=True)[:top_n_words]
                all_words.update(top)

        word_list = list(all_words)[:top_n_words]
        matrix_data = []
        for tid in sorted_ids:
            tsd = topics_data[tid].get("word_scores", {}) if isinstance(topics_data.get(tid), dict) else {}
            row = [float(tsd.get(w, 0)) for w in word_list]
            matrix_data.append(row)

        fig = go.Figure(data=go.Heatmap(
            z=matrix_data,
            x=word_list,
            y=[f"Topic {tid}" for tid in sorted_ids],
            colorscale="Viridis",
        ))

        fig.update_layout(
            title=f"Top Word Dominance by Topic (top {len(word_list)} words)",
            template="plotly_white",
            height=max(300, len(sorted_ids) * 40),
        )
        os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
        fig.write_image(output_path, width=1200)
        return output_path


class TopicOverlapAnalyzer:

    @staticmethod
    def compute_jaccard(words_a, words_b):
        set_a = set(words_a)
        set_b = set(words_b)
        if not set_a and not set_b:
            return 0.0
        intersection = set_a & set_b
        union = set_a | set_b
        return round(len(intersection) / len(union), 4)

    @staticmethod
    def cosine_similarity_between(vec1, vec2):
        common = set(vec1.keys()) & set(vec2.keys())
        if not common:
            return 0.0
        v1 = np.array([vec1[w] for w in common])
        v2 = np.array([vec2[w] for w in common])
        dot_val = float(np.dot(v1, v2))
        norm_val = float(np.linalg.norm(v1) * np.linalg.norm(v2)) + 1e-12
        return round(dot_val / norm_val, 4)


class TopicModelingPipeline:

    def __init__(self, db_path=None):
        self.db_path = db_path or ".reddit_db.sqlite3"
        self.subreddit_texts = {}
        self.results_cache = {}
        self.summary_cache = CorpusSummary()

    def collect_posts(self, subreddit_filter=None, limit=500):
        from sqlite3 import connect as sql_connect
        conn = sql_connect(self.db_path)
        cursor = conn.cursor()

        if subreddit_filter:
            q = "SELECT id, subreddit, title, body FROM posts WHERE subreddit = ? LIMIT ?"
            cursor.execute(q, (subreddit_filter, limit))
        else:
            q = "SELECT id, subreddit, title, body FROM posts LIMIT ?"
            cursor.execute(q, (limit,))

        subs = defaultdict(list)
        for row in cursor.fetchall():
            sr = str(row[1])
            txt = "{} {}".format(
                row[2] or "", row[3] or ""
            ).strip()[:1000]
            if len(txt) > 50:
                subs[sr].append(txt)

        conn.close()
        self.subreddit_texts = dict(subs)
        logger.info("Collected texts for %d subreddits.", len(subs))
        return self.subreddit_texts

    def analyze(
        self,
        lda_topics=10,
        nmf_topics=None,
        find_optimal_k=False,
        kmin=3,
        kmax=25,
        kstep=4,
    ):
        if not self.subreddit_texts:
            logger.warning("No texts collected.")
            return {}

        results = {}
        all_texts = []
        for sr in sorted(self.subreddit_texts.keys()):
            all_texts.extend(self.subreddit_texts[sr])

        if len(all_texts) < 10:
            logger.warning("Not enough text data (%d docs).", len(all_texts))
            return {}

        bow, dictionary, _ = TopicPreprocessor.prepare_corpus(all_texts)
        start_time = time.time()

        # --- LDA ---
        t_start = time.time()
        lda_result = LDATrainer.train(
            bow, dictionary, num_topics=lda_topics, passes=20
        )
        logger.info("LDA done in %.1fs (C_v=%.4f).", time.time() - t_start, lda_result["c_v_coherence"])
        lda_result["training_time_seconds"] = round(time.time() - t_start, 2)
        results["lda"] = lda_result

        # --- NMF ---
        nmf_targets = nmf_topics or lda_topics
        t_nmf = time.time()
        nmf_result = NMFTrainer.fit(all_texts, num_topics=nmf_targets)
        logger.info("NMF done in %.1fs.", time.time() - t_nmf)
        results["nmf"] = nmf_result

        # --- Optimal k (optional grid search) ---
        if find_optimal_k:
            logger.info("Running optimal-k grid search ...")
            ok_result = LDATrainer.find_optimal_topics(
                bow, dictionary, kmin=kmin, kmax=kmax, kstep=kstep
            )
            results["optimal_k_analysis"] = ok_result

        # --- Cross-subreddit overlap ---
        if len(self.subreddit_texts) > 1:
            logger.info("Computing cross-subreddit topic overlap ...")
            overlap = self._compute_cross_subreddit_overlap(lda_topics)
            results["cross_subreddit_overlap"] = overlap

        elapsed = time.time() - start_time
        results["total_time_seconds"] = round(elapsed, 2)
        return results

    def _compute_cross_subreddit_overlap(self, lda_num):
        srs = sorted(self.subreddit_texts.keys())[:5]
        sr_dominant = {}

        for sr in srs:
            texts = self.subreddit_texts[sr]
            if len(texts) > 200:
                texts = texts[:200]
            bow_sr, dict_sr, _ = TopicPreprocessor.prepare_corpus(texts)
            res = LDATrainer.train(bow_sr, dict_sr, num_topics=min(5, lda_num), passes=10)
            td = res.get("topics_data", {})
            dominants = []
            for tval in td:
                entry = td[tval] if isinstance(td, dict) else {}
                if isinstance(entry, dict):
                    dominants.extend(entry.get("dominant_words", []))
            sr_dominant[sr] = dominants

        overlap_map = defaultdict(float)
        for i_a in range(len(srs)):
            for j_b in range(i_a + 1, len(srs)):
                wa = sr_dominant.get(str(srs[i_a]), []) or []
                wb = sr_dominant.get(str(srs[j_b]), []) or []
                jc = TopicOverlapAnalyzer.compute_jaccard(wa, wb)
                key_a = "{}<->{}".format(srs[i_a], srs[j_b])
                overlap_map[key_a] = jc

        return dict(overlap_map)


class TopicReportFormatter:

    @staticmethod
    def to_json(pipeline_results):
        data = {
            "report_type": "topic_modeling",
            "generated_at": datetime.now().isoformat(),
        }

        if "lda" in pipeline_results:
            lda = pipeline_results["lda"]
            topics_map = {}
            tdata_map = lda.get("topics_data")
            if isinstance(tdata_map, dict):
                for tid_val, tdata in tdata_map.items():
                    if isinstance(tdata, dict):
                        ws = tdata.get("word_scores", {})
                        avg_s = round(float(np.mean(list(ws.values()))), 4) if ws else 0.0
                        topics_map[tid_val] = {
                            "dominant_words": tdata.get("dominant_words", []),
                            "avg_word_score": avg_s,
                            "description": tdata.get("description", ""),
                        }

            data["lda"] = {
                "num_topics": lda.get("num_topics", 0),
                "u_mass_coherence": round(float(lda.get("u_mass_coherence", 0)), 4),
                "c_v_coherence": round(float(lda.get("c_v_coherence", 0)), 4),
                "topics": topics_map,
            }

        if "nmf" in pipeline_results:
            nmf = pipeline_results["nmf"]
            data["nmf"] = {
                "num_topics": nmf.get("num_topics", 0),
                "vocabulary_size": nmf.get("vocabulary_size", 0),
                "doc_count": nmf.get("doc_count", 0),
                "training_time_seconds": nmf.get("training_time_seconds", 0),
            }

        if "optimal_k_analysis" in pipeline_results:
            ok = pipeline_results["optimal_k_analysis"]
            data["optimal_k"] = {"best_k": ok.get("best_k"), "num_topics": ok.get("num_topics")}

        data["total_time_seconds"] = pipeline_results.get("total_time_seconds", 0)
        return json.dumps(data, indent=2, default=str)

    @staticmethod
    def to_csv(pipeline_results):
        lines_out = ["subreddit,dominant_words,coherence,c_v_score,doc_count"]
        if "lda" in pipeline_results:
            lda_data = pipeline_results["lda"]
            td_val = lda_data.get("topics_data")
            doc_count = pipeline_results.get("nmf", {}).get("doc_count", 0) if isinstance(pipeline_results.get("nmf"), dict) else 0

            if isinstance(td_val, dict):
                for tid_key in sorted(td_val.keys()):
                    entry = td_val[tid_key]
                    if not isinstance(entry, dict):
                        continue
                    wd_str = "; ".join((entry.get("dominant_words", [])[:5]))
                    ws_map = entry.get("word_scores", {})
                    co_val = round(float(np.mean(list(ws_map.values()))), 4) if ws_map else 0.0
                    cv_val = round(float(lda_data.get("c_v_coherence", 0)), 4)
                    row = "{},{},{},{:.4f},{:.4f},{}".format(
                        "topic_{}".format(tid_key), wd_str, co_val, cv_val, doc_count
                    )

        return "\n".join(lines_out)

    @staticmethod
    def to_markdown(pipeline_results):
        lines = [
            "# Topic Modeling Report",
            "",
            "Generated: {}".format(datetime.now().strftime("%Y-%m-%d %H:%M:%S")),
            "",
        ]

        if "lda" in pipeline_results:
            lda_md = pipeline_results["lda"]
            lines.append("## LDA Model")
            lines.append("")
            lines.append("- Topics: {}".format(lda_md.get("num_topics", 0)))

            um_val = round(float(lda_md.get("u_mass_coherence", 0)), 4)
            cv_val = round(float(lda_md.get("c_v_coherence", 0)), 4)
            lines.append("- U-Mass Coherence: {}".format(um_val))
            lines.append("- C_v Coherence: {}".format(cv_val))
            lines.append("")

            td_lda = lda_md.get("topics_data")
            if isinstance(td_lda, dict):
                for tid_key in sorted(td_lda.keys()):
                    entry = td_lda[tid_key]
                    if not isinstance(entry, dict):
                        continue
                    wd_str2 = "; ".join((entry.get("dominant_words", [])[:5]))
                    desc_md = entry.get("description", "")
                    lines.append("")
                    lines.append("### Topic {}: [{}]".format(tid_key, wd_str2))
                    lines.append(desc_md)

        return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="Topic Modeling Pipeline for crawled Reddit content.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  python topic_modeling.py --lda-topics 10 --nmf-topics 8\n"
            "  python topic_modeling.py --find-optimal-k --kmin 3 --kmax 25\n"
        ),
    )

    parser.add_argument("--lda-topics", type=int, default=10)
    parser.add_argument("--nmf-topics", type=int, default=None)
    parser.add_argument("--find-optimal-k", action="store_true")
    parser.add_argument("--kmin", type=int, default=3)
    parser.add_argument("--kmax", type=int, default=25)
    parser.add_argument("--kstep", type=int, default=4)
    parser.add_argument("--subreddit", "-s", default=None)
    parser.add_argument("--database", "-d", default=".reddit_db.sqlite3")
    parser.add_argument("--output", "-o", default="./output")

    args = parser.parse_args()

    logger.info("Starting topic modeling pipeline ...")
    start_time = time.time()

    try:
        pipeline = TopicModelingPipeline(db_path=args.database)
        collected = pipeline.collect_posts(subreddit_filter=args.subreddit, limit=500)

        if not collected:
            logger.warning("No texts collected. Check database path.")
            return 1

        results = pipeline.analyze(
            lda_topics=args.lda_topics,
            nmf_topics=args.nmf_topics,
            find_optimal_k=args.find_optimal_k,
            kmin=args.kmin,
            kmax=args.kmax,
            kstep=args.kstep,
        )

        if not results:
            logger.warning("No pipeline results produced.")
            return 1

        outdir = args.output
        os.makedirs(outdir, exist_ok=True)

        json_path = os.path.join(outdir, "topic_modeling_report.json")
        with open(json_path, "w") as fh:
            fh.write(TopicReportFormatter.to_json(results))

        viz_dir = os.path.join(outdir, "visualizations")
        os.makedirs(viz_dir, exist_ok=True)

        lda_r = results.get("lda", {})
        if isinstance(lda_r, dict):
            td_inner = lda_r.get("topics_data")
            if isinstance(td_inner, dict):
                vis_hm = os.path.join(viz_dir, "topic_dominance_heatmap.png")
                TopicVisualizer.plot_topic_heatmap(td_inner, output_path=vis_hm)

        elapsed = time.time() - start_time
        print("")
        print("  Topics extracted    : LDA {}, NMF {}".format(args.lda_topics, args.nmf_topics))
        print("  Subreddits analyzed : {}".format(len(collected)))
        print("  Report              : {}".format(json_path))
        print("  Elapsed             : {:.1f}s".format(elapsed))

    except KeyboardInterrupt:
        logger.info("Pipeline interrupted by user.")
        return 1
    except Exception as exc:
        logger.error("Pipeline failed: %s", str(exc))
        return 1

    return 0


if __name__ == "__main__":
    exit(main())
