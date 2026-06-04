"""
KeyPhrase Extractor — Keyword/Phrase Extraction Module

Implements multi-strategy theme discovery for crawled Reddit content:
  1. TF-IDF + Top-N keywords (classical information retrieval)
  2. KeyBERT (neural keyphrase extraction using SBERT embeddings)
  3. Collocation / bigram phrase mining for domain-specific terms

Designed to work as a standalone CLI tool and as an importable module.
Outputs: per-subreddit theme reports in JSON, CSV, and Markdown formats.

Author: DS Analyst Agent (Zarrouk6969)
Date: 2026-06-04
"""

from __future__ import annotations

import json
import logging
import os
import re
import sqlite3
from collections import Counter, defaultdict
from dataclasses import dataclass, field, asdict
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd

# Try soft-import of KeyBERT; module works without it (graceful fallback)
try:
    from keybert import KeyBERT as _KeyBERT  # noqa: F401
    HAS_KEYBERT = True
except ImportError:
    HAS_KEYBERT = False

logger = logging.getLogger("ds-analyst.keyphrase")


# ───────────────────────── Constants ──────────────────────────

_STOP_WORDS: frozenset = frozenset({
    "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your",
    "yours", "yourself", "yourselves", "he", "him", "his", "himself", "she", "her",
    "hers", "herself", "it", "its", "itself", "they", "them", "their", "theirs",
    "themselves", "what", "which", "who", "whom", "this", "that", "these", "those",
    "am", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
    "having", "do", "does", "did", "doing", "a", "an", "the", "and", "but", "if",
    "or", "because", "as", "until", "while", "of", "at", "by", "for", "with",
    "about", "against", "between", "through", "during", "before", "after", "above",
    "below", "to", "from", "up", "down", "in", "out", "on", "off", "over", "under",
    "again", "further", "then", "once", "here", "there", "when", "where", "why",
    "how", "all", "both", "each", "few", "more", "most", "other", "some", "such",
    "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very", "s",
    "t", "can", "will", "just", "don", "should", "now", "d", "ll", "m", "o", "re",
    "ve", "y", "ain", "aren", "couldn", "didn", "doesn", "hadn", "hasn", "haven",
    "isn", "ma", "mightn", "mustn", "needn", "shan", "shouldn", "wasn", "weren",
    "won", "wouldn", "'ll", "'re", "'ve", "n't",
    "also", "get", "got", "like", "make", "went", "going", "know", "think",
    "say", "said", "tell", "one", "two", "first", "even", "well", "much",
    "many", "thing", "things", "way", "yeah", "man", "people", "bit",
})


# ────────────────────── Data Classes ───────────────────────────

@dataclass
class KeywordResult:
    """Represents a single keyword/phrase extraction result."""
    term: str
    score: float
    frequency: int
    df: int
    tfidf_score: Optional[float] = None


@dataclass
class ThemeReport:
    """Full theme report for a single subreddit."""
    subreddit: str
    total_docs: int
    top_keywords: List[KeywordResult] = field(default_factory=list)
    keybert_phrases: List[dict] = field(default_factory=list)
    bigram_phrases: List[Tuple[str, float]] = field(default_factory=list)
    generated_at: str = ""

    def to_dict(self) -> dict:
        return asdict(self)


# ────────────────── Text Preprocessing Pipeline ───────────────

class TextPreprocessor:
    """Multi-stage text cleaning pipeline for Reddit content."""

    URL_RE = re.compile(r'https?://\S+|www\.\S+')
    USER_RE = re.compile(r'@[A-Za-z0-9_]+')

    @classmethod
    def clean(cls, text: str) -> str:
        if not text or not isinstance(text, str):
            return ""
        text = cls.URL_RE.sub(" LINK ", text)
        text = cls.USER_RE.sub(" USER ", text)
        text = re.sub(r'\s+', ' ', text).strip()
        return text.lower()

    @classmethod
    def clean_batch(cls, texts: List[str]) -> List[str]:
        return [cls.clean(t) for t in texts]


# ──────────────── TF-IDF Keyword Extraction ──────────────────

class TfidfExtractor:
    """Classical TF-IDF keyword extractor (no external ML needed)."""

    @staticmethod
    def _tokenize(text: str) -> List[str]:
        cleaned = TextPreprocessor.clean(text)
        tokens = re.findall(r'[a-z]{3,}', cleaned)
        return [t for t in tokens if t not in _STOP_WORDS]

    @classmethod
    def compute_idf(cls, doc_list: List[str]) -> Tuple[Dict[str, float], Counter]:
        """Return (idf_map, df_counter)."""
        n_docs = len(doc_list)
        df: Counter = Counter()
        for tokens in map(cls._tokenize, doc_list):
            df.update(set(tokens))
        idf = {t: np.log(n_docs / (df[t] + 1)) + 1.0 for t in df}
        return dict(idf), df          # type: ignore[return-value]

    @classmethod
    def compute_tfidf(
        cls,
        query_text: str,
        idf_map: Dict[str, float],
        top_n: int = 20,
    ) -> List[KeywordResult]:
        tokens = cls._tokenize(query_text)
        tf = Counter(tokens)
        total = len(tokens) if tokens else 1
        scores: List[KeywordResult] = []
        for term, count in tf.items():
            val = (count / total) * idf_map.get(term, 1.0)
            scores.append(KeywordResult(term=term, score=val, frequency=count, df=0,
                                        tfidf_score=round(val, 6)))
        scores.sort(key=lambda x: x.tfidf_score or 0.0, reverse=True)
        return scores[:top_n]

    @classmethod
    def corpus_rank(
        cls,
        doc_list: List[str],
        idf_map: Dict[str, float],
        df_map: Counter,
        top_n: int = 30,
    ) -> List[KeywordResult]:
        agg: defaultdict = defaultdict(lambda: [0.0, 0])      # [sum_tfidf, freq]
        for doc in doc_list:
            tokens = cls._tokenize(doc)
            total = len(tokens) if tokens else 1
            tf = Counter(tokens)
            for term, count in tf.items():
                agg[term][0] += (count / total) * idf_map.get(term, 1.0)
                agg[term][1] += 1
        results: List[KeywordResult] = []
        for term, (s, f) in agg.items():
            results.append(KeywordResult(term=term, score=s, frequency=f, df=df_map.get(term, 0),
                                         tfidf_score=s))
        results.sort(key=lambda x: x.score, reverse=True)
        return results[:top_n]


# ─────────────── KeyBERT Phrase Extraction ──────────────────

class KeyBERTExtractor:
    """Neural keyphrase extraction using KeyBERT + SBERT embeddings."""

    def __init__(self, model_name: str = "all-MiniLM-L6-v2") -> None:
        if not HAS_KEYBERT:
            raise ImportError(
                "KeyBERT requires `pip install keybert sentence-transformers`"
            )
        from keybert import KeyBERT as _KeyBERTImpl  # noqa: local import in guard
        self._model = _KeyBERTImpl(model=model_name)  # type: ignore[assignment]

    def extract(self, texts: List[str], top_n: int = 15,
                min_df: int = 2, max_df: float = 0.95) -> List[dict]:
        cleaned = TextPreprocessor.clean_batch(texts)
        query = cleaned[0] if cleaned else ""
        pairs = self._model.extract_keywords(
            query,
            keyphrase_ngram_range=(1, 3),
            stop_words="english",
            top_n=top_n,
            min_df=min_df,
            max_df=max_df,
        )
        return [{"term": t, "score": round(float(s), 6)} for t, s in pairs]


# ─────────────── Collocation Mining ──────────────────

class CollocationExtractor:
    """Mine bigram collocations with PMI-style scores."""

    @classmethod
    def _bigrams(cls, tokens: List[str]) -> List[Tuple[str, str]]:
        return list(zip(tokens, tokens[1:])) if len(tokens) >= 2 else []

    @classmethod
    def extract(cls, doc_list: List[str], top_n: int = 30
                ) -> List[Tuple[str, float]]:
        freq: Counter = Counter()
        total = max(len(doc_list), 1)
        for tokens in [TfidfExtractor._tokenize(d) for d in doc_list]:
            freq.update(cls._bigrams(tokens))
        scored: List[Tuple[str, float]] = []
        for (w1, w2), raw in freq.items():
            if w1 in _STOP_WORDS or w2 in _STOP_WORDS:
                continue
            score = raw / total * np.log(raw + 1.0) + 1.0
            scored.append((f"{w1}_{w2}", round(float(score), 6)))
        scored.sort(key=lambda x: x[1], reverse=True)
        return scored[:top_n]


# ──────────────── Database Integration ──────────────────

class RedditDBConnector:
    """Connect to the SQLite database used by the crawler backend."""

    DEFAULT_DB = os.path.join(
        os.path.dirname(os.path.dirname(__file__)), "data", "reddit_crawler.db"
    )

    def __init__(self, db_path: Optional[str] = None) -> None:
        self.db_path = db_path or self.DEFAULT_DB
        self._conn: Optional[sqlite3.Connection] = None

    def connect(self) -> sqlite3.Connection:
        if self._conn is None:
            if not os.path.exists(self.db_path):
                raise FileNotFoundError(
                    f"Database not found at {self.db_path}. Run the crawler first."
                )
            self._conn = sqlite3.connect(self.db_path)
            self._conn.row_factory = sqlite3.Row
        return self._conn    # type: ignore[return-value]

    def get_subreddits(self) -> List[str]:
        conn = self.connect()
        cur = conn.execute(
            "SELECT DISTINCT subreddit FROM posts GROUP BY subreddit ORDER BY subreddit"
        )
        return [row["subreddit"] for row in cur.fetchall()]     # type: ignore[index]

    def get_posts_for_subreddit(self, subreddit: str, limit: int = 500) -> List[dict]:
        conn = self.connect()
        cur = conn.execute(
            "SELECT title, body_or_text, score, num_comments FROM posts "
            "WHERE subreddit = ? ORDER BY created_at DESC LIMIT ?",
            (subreddit, limit),
        )
        return [dict(row) for row in cur.fetchall()]     # type: ignore[arg-type]

    def get_all_posts(self, limit: int = 2000) -> List[dict]:
        conn = self.connect()
        cur = conn.execute(
            "SELECT subreddit, title, body_or_text, score FROM posts "
            "ORDER BY created_at LIMIT ?", (limit,)
        )
        return [dict(row) for row in cur.fetchall()]    # type: ignore[arg-type]

    def close(self) -> None:
        if self._conn is not None:
            self._conn.close()
            self._conn = None


# ─────────────── Theme Report Builder ──────────────────

class ThemeReportBuilder:
    """Assemble per-subreddit theme reports from all extraction strategies."""

    def __init__(self) -> None:
        self._report: Optional[ThemeReport] = None

    @property
    def report(self) -> Optional[ThemeReport]:
        return self._report

    @report.setter
    def report(self, value: ThemeReport) -> None:
        self._report = value

    def build(
        self,
        subreddit: str,
        texts: List[str],
        total_docs: int,
        top_n: int = 20,
        use_keybert: bool = True,
    ) -> ThemeReport:
        idf_map, df_map = TfidfExtractor.compute_idf(texts)

        tfidf_kw = TfidfExtractor.corpus_rank(texts, idf_map, df_map, top_n=top_n)

        kb_phrases: List[dict] = []
        if use_keybert and HAS_KEYBERT:
            try:
                extractor = KeyBERTExtractor()
                kb_phrases = extractor.extract(texts, top_n=top_n)
            except Exception as exc:   # pragma: no cover
                logger.warning("KeyBERT extraction failed: %s", exc)

        collocations = CollocationExtractor.extract(texts, top_n=top_n // 2)

        self.report = ThemeReport(
            subreddit=subreddit,
            total_docs=total_docs,
            top_keywords=tfidf_kw,
            keybert_phrases=kb_phrases,
            bigram_phrases=collocations,
            generated_at=datetime.utcnow().isoformat(),
        )
        assert self._report is not None, "report should always be set"
        return self._report  # type: ignore[return-value]


# ─────────────── Output Formatters ──────────────────

class ReportFormatter:
    """Format theme reports into JSON / CSV / Markdown."""

    @staticmethod
    def to_json(report: ThemeReport) -> str:
        return json.dumps(report.to_dict(), indent=2, default=str)

    @staticmethod
    def to_csv(reports: List[ThemeReport]) -> pd.DataFrame:
        rows: List[dict] = []
        for r in reports:
            for kw in r.top_keywords[:10]:
                rows.append({
                    "subreddit": r.subreddit,
                    "keyword": kw.term,
                    "score": kw.score,
                    "frequency": kw.frequency,
                    "df": kw.df,
                    "tfidf_score": kw.tfidf_score,
                })
        return pd.DataFrame(rows)

    @staticmethod
    def to_markdown(reports: List[ThemeReport]) -> str:
        lines = ["# Reddit Theme Reports", "",
                 f"> Generated: {datetime.utcnow().isoformat()}"]
        for r in reports:
            lines.append(f"\n## r/{r.subreddit}")
            lines.append(
                f"**Documents:** {r.total_docs} | **Generated:** {r.generated_at}\n")
            if r.keybert_phrases:
                lines.append("### KeyBERT Phrases (Top 10)")
                lines.append("| Phrase | Score |")
                lines.append("|--------|-------|")
                for p in r.keybert_phrases[:10]:
                    lines.append(f"| {p['term']} | {float(p['score']):.4f} |")
            if r.top_keywords:
                lines.append("\n### Top TF-IDF Keywords (Top 10)")
                lines.append("| Keyword | Score | Freq | DF |")
                lines.append("|---------|-------|------|----|")
                for kw in r.top_keywords[:10]:
                    lines.append(
                        f"| {kw.term} | {float(kw.score):.4f} "
                        f"| {kw.frequency} | {kw.df} |")
            if r.bigram_phrases:
                lines.append("\n### Top Collocations (Top 5)")
                lines.append("| Bigram | Score |")
                lines.append("|--------|-------|")
                for bg, s in r.bigram_phrases[:5]:
                    lines.append(f"| {bg} | {float(s):.4f} |")
        return "\n".join(lines)


# ─────────────── CLI Entry-point ──────────────────

def main() -> None:
    """Run keyword/phrase extraction on the crawled Reddit data."""
    import argparse

    parser = argparse.ArgumentParser(
        description="KeyPhrase Extractor — TF-IDF, KeyBERT & Collocations",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  python keyphrase_extraction.py --all-subreddits\n"
            "  python keyphrase_extraction.py -s r/MachineLearning -l 200 -f json csv\n"
        ),
    )
    parser.add_argument("--db", default=None, help="SQLite database path")
    parser.add_argument("-s", "--subreddit", default=None, help="Single subreddit")
    parser.add_argument("-l", "--limit", type=int, default=500,
                        help="Max posts per subreddit (default 500)")
    parser.add_argument("-n", "--top-n", type=int, default=20,
                        help="Top keywords/phrases to return")
    parser.add_argument("-f", "--format", nargs="+", default=["json", "csv"],
                        choices=["json", "csv", "md", "all"],
                        help="Output format(s)")
    parser.add_argument("--no-keybert", action="store_true",
                        help="Skip KeyBERT (TF-IDF + collocations only)")
    parser.add_argument("-o", "--output-dir", default="output/keyphrase_reports",
                        help="Output dir")

    args = parser.parse_args()
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
    )

    connector = RedditDBConnector(args.db)
    try:
        target = (
            sorted(connector.get_subreddits())[:1]
            if args.subreddit
            else sorted(connector.get_subreddits())
        )
        # If a single subreddit was requested, filter to it exactly
        if args.subreddit and args.subreddit.startswith("r/"):
            target = [args.subreddit] if args.subreddit in connector.get_subreddits() else []

        if not target:
            print("No subreddits found — did the crawler run yet?")
            return

        output_dir = Path(args.output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)
        formatter = ReportFormatter()
        all_reports: List[ThemeReport] = []

        for sdb in target:
            posts = connector.get_posts_for_subreddit(sdb, limit=args.limit)
            if not posts:
                print(f"⚠  No posts for r/{sdb}, skipping")
                continue
            texts: List[str] = []
            for p in posts:
                title = TextPreprocessor.clean(p.get("title", "") or "")   # type: ignore[union-attr]
                body = TextPreprocessor.clean(p.get("body_or_text", "") or "")  # type: ignore[union-attr]
                combined = f"{title}. {body}" if title and body else (title or body)
                if combined:
                    texts.append(combined)

            logger.info("r/%s — %d text segments", sdb, len(texts))

            builder = ThemeReportBuilder()
            report = builder.build(
                subreddit=sdb,
                texts=texts,
                total_docs=len(posts),
                top_n=args.top_n,
                use_keybert=not args.no_keybert,
            )
            all_reports.append(report)

        formats = args.format if "all" not in args.format else ["json", "csv", "md"]
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        ext_map = {"json": ".json", "csv": ".csv", "md": ".md"}

        for fmt in formats:
            path = output_dir / f"keyphrase_reports_{ts}{ext_map.get(fmt, '')}"
            if fmt == "json":
                data = {r.subreddit: r.to_dict() for r in all_reports}
                path.write_text(json.dumps(data, indent=2, default=str))
                print(f"📄 JSON → {path}")
            elif fmt == "csv":
                df = formatter.to_csv(all_reports)
                if not df.empty:
                    csv_path = output_dir / f"keyphrase_keywords_{ts}.csv"
                    df.to_csv(csv_path, index=False)
                    print(f"📄 CSV → {csv_path}")
                else:
                    print("⚠  No data for CSV")
            elif fmt == "md":
                md_text = formatter.to_markdown(all_reports)
                path.write_text(md_text)
                print(f"📄 Markdown → {path}")

        print(f"\n{'='*60}")
        print(f"✅ Theme analysis complete — {len(all_reports)} subreddit(s)")
        for r in all_reports:
            n_kw = len(r.top_keywords) if r.top_keywords else 0
            n_kb = len(r.keybert_phrases) if r.keybert_phrases else 0
            n_bg = len(r.bigram_phrases) if r.bigram_phrases else 0
            print(f"   r/{r.subreddit}: {n_kw} TF-IDF + {n_kb} KeyBERT + {n_bg} bigrams")
        print(f"{'='*60}")

    finally:
        connector.close()


if __name__ == "__main__":
    main()
