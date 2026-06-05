"""
Data Export Utilities -- Rotation Task B Part 3

Export sentiment/keyword/topic analysis results to CSV and Excel formats.
Supports per-subreddit breakdowns, raw thread lists, and aggregated stats.

Usage:
    from visual.export_util import CSVExporter, ExcelExporter

    exporter = CSVExporter(stats_data)
    path = exporter.to_csv("/tmp/sentiment_export.csv")

    excel_exp = ExcelExporter(stats_data, sheets=["sentiment", "keywords"])
    xlsx_path = excel_exp.to_excel("/tmp/analysis_report.xlsx")

Dependencies: pip install pandas openpyxl (Excel format only)
"""

from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Optional, Any
import csv


# --------------------------------------------------------------------------- #
#  CSV Exporter                                                               #
# ---------------------------------------------------------------------------- #

@dataclass
class CSVExportConfig:
    """Configuration for CSV export."""
    delimiter: str = ","        # Column separator (comma, tab, semicolon)
    encoding: str = "utf-8"     # File encoding
    include_header: bool = True  # First row = column headers
    quote_chars: str = '"'      # Character used to enclose values with delimiters
    date_format: Optional[str] = None  # strftime format for datetime columns


class CSVExporter:
    """Export analysis results to one or more CSV files."""

    def __init__(self, data: dict | list[dict], config: Optional[CSVExportConfig] = None):
        self.data = data
        self.config = config or CSVExportConfig()

    # ---- single table export -----------------------------------------------

    def to_csv(
        self,
        file_path: str,
        table_name: str = "data",
        **kwargs: Any
    ) -> Optional[str]:
        """Write data as a single CSV table.

        Args:
            file_path: Output path (e.g., "/tmp/export.csv"). Auto-creates directory.
            table_name: Logical name for this table within export context (not stored in CSV.)
            **kwargs: Additional keyword arguments passed to csv.writer constructor.

        Returns:
            The output file path as a string on success; None on error.
        """
        if not self.data:
            return None

        # Convert nested dict/list-of-dicts format to rows (flat lists) for writing.
        if isinstance(self.data, dict):
            headers = list(self.data.keys())
            rows = [list(self.data.values())]

        elif isinstance(self.data, list):
            if len(self.data) == 0:
                return None

            first_item = self.data[0]

            if isinstance(first_item, dict):
                headers = list(first_item.keys())
                rows = [list(item.values()) for item in self.data]

            elif isinstance(first_item, (list, tuple)):
                headers = [f"Col_{i+1}" for i in range(len(first_item))]
                rows = [list(item) for item in self.data]

            else:
                # Fallback: treat as generic list
                headers = ["value"]
                rows = [[item] for item in self.data]
        else:
            return None

        # Write the file
        Path(file_path).parent.mkdir(parents=True, exist_ok=True)
        with open(file_path, "w", encoding=self.config.encoding, newline="") as f:
            writer = csv.writer(
                f,
                delimiter=self.config.delimiter,
                quotechar=self.config.quote_chars,
                **kwargs,
            )
            if self.config.include_header:
                writer.writerow(headers)
            for row in rows:
                writer.writerow(row)

        return file_path

    def to_json(self, file_path: str) -> Optional[str]:
        """Export data as a JSON file."""
        import json
        Path(file_path).parent.mkdir(parents=True, exist_ok=True)
        with open(file_path, "w", encoding="utf-8") as f:
            json.dump(self.data, f, indent=2, default=str)
        return file_path


# --------------------------------------------------------------------------- #
#  Excel Exporter                                                             #
# ---------------------------------------------------------------------------- #

class ExcelExporter:
    """Export analysis results to multi-sheet Excel .xlsx files."""

    def __init__(self):
        self._sheets: dict[str, Any] = {}

    def add_sheet(self, name: str, data: dict | list[dict]) -> "ExcelExporter":
        """Register a sheet and its data. Returns self for chaining."""
        self._sheets[name] = data
        return self

    def to_excel(
        self,
        file_path: str,
    ) -> Optional[str]:
        """Write all registered sheets to an .xlsx file.

        Requires: pip install openpyxl
        """
        if not self._sheets:
            return None

        try:
            import pandas as pd  # type: ignore[import-untyped]
        except ImportError:
            raise ImportError("pandas is required for Excel export.\n    pip install pandas openpyxl")

        Path(file_path).parent.mkdir(parents=True, exist_ok=True)

        with pd.ExcelWriter(file_path, engine="openpyxl") as writer:
            for sheet_name, data in self._sheets.items():
                df = pd.DataFrame(data) if isinstance(data, list) else pd.DataFrame([data])
                df.to_excel(writer, sheet_name=sheet_name[:31], index=False)  # Excel caps sheet names at 31
        return file_path


# --------------------------------------------------------------------------- #
#  Unified Analyst Report Generator                                           #
# ---------------------------------------------------------------------------- #

@dataclass
class AnalyticsReport:
    """Consolidated analytics report combining sentiment, keyword, and topic outputs."""
    title: str = "Reddit Analytics Report"
    generated_at: Optional[str] = None  # f-string set at runtime
    sentiment_data: Optional[dict] = None
    keywords_data: Optional[dict] = None  # TF-IDF + KeyBERT output dict
    topics_data: Optional[dict] = None    # LDA output dict
    export_dir: str = "/tmp/"

    def save(
        self,
        base_name: str = "analysis_report",
        as_csv: bool = True,
        as_excel: bool = False,
        as_json: bool = True,
    ) -> list[str]:
        """Save the report to disk in requested formats.

        Returns a list of generated file paths.
        """
        import datetime
        self.generated_at = datetime.datetime.now(datetime.timezone.utc).isoformat()
        self.title += f" ({self.generated_at[:10]})"

        generated_paths: list[str] = []

        # ── Sentiment per-subreddit CSV ──
        if as_csv and self.sentiment_data:
            exp = CSVExporter(self.sentiment_data)
            for sub_name, stats in self.sentiment_data.items():
                path = f"{self.export_dir}{base_name}_sentiment_{sub_name}.csv"
                if path not in generated_paths:
                    exp2 = CSVExporter([stats])  # single-row dict → one-row CSV
                    r = exp2.to_csv(path)
                    if r and r not in generated_paths:
                        generated_paths.append(r)

        # ── Overall summary JSON ──
        if as_json and self.sentiment_data:
            import json
            report_dict = {
                "title": self.title,
                "generated_at": self.generated_at,
                "summary": {
                    k: v for k, v in self.sentiment_data.items()
                    if k not in ("per_subreddit", "threads") and isinstance(v, (int, float, str))
                },
            }
            path = f"{self.export_dir}{base_name}_summary.json"
            generated_paths.append(expose_to_json(report_dict, path))

        # ── Per-thread CSV ──
        if as_csv and self.sentiment_data and "threads" in self.sentiment_data:
            threads = self.sentiment_data["threads"]  # list of ThreadSentiment dicts
            file_list_path = f"{self.export_dir}{base_name}_sentiment_threads.csv"
            if not Path(file_list_path).exists() or True:
                exp_threads = CSVExporter(threads)
                r = exp_threads.to_csv(file_list_path)
                if r and r not in generated_paths:
                    generated_paths.append(r)

        # ── Excel (if requested) ──
        if as_excel:
            xlsx_exp = ExcelExporter()

            if self.sentiment_data:
                # Flatten per-subreddit sentiment for one sheet
                sent_records: list[dict] = []
                for sub_name, stats in self.sentiment_data.items():
                    sent_records.append({**self._flatten(stats), "subreddit": sub_name} if isinstance(stats, dict) else {"_raw": str(stats)})
                if sent_records:
                    xlsx_exp.add_sheet("Sentiment", sent_records)

            if self.keywords_data and "per_subreddit" in self.keywords_data:
                kw_records: list[dict] = []
                for sub_name, kdata in self.keywords_data["per_subreddit"].items():
                    key_list = kdata.get("keywords", []) if isinstance(kdata, dict) else []
                    # Take top keyword only for compact view
                    top_kw = key_list[0] if isinstance(key_list, list) and key_list else ("", 0.0)
                    kw_records.append({
                        "subreddit": sub_name,
                        "top_keyword": top_kw[0] if isinstance(top_kw, (list, tuple)) else str(top_kw),
                        "score": top_kw[1] if isinstance(top_kw, (list, tuple)) and len(top_kw) > 1 else str(top_kw),
                    })
                if kw_records:
                    xlsx_exp.add_sheet("Keywords", kw_records)

            if self.topics_data:
                topic_records: list[dict] = []
                for tid, terms in self.topics_data.get("topics_per_topic", {}).items():
                    label = self.topics_data.get("topic_labels", {}).get(tid, f"Topic_{tid}")
                    # Pick top-5 terms as a string field
                    top_terms_str = " | ".join(f"{t} ({w:.3f})" for t, w in terms[:5]) if isinstance(terms, list) else ""
                    topic_records.append({
                        "topic_id": tid,
                        "label": label,
                        "top_terms": top_terms_str,
                    })
                if topic_records:
                    xlsx_exp.add_sheet("Topics", topic_records)

            if xlsx_exp._sheets:
                xls_path = f"{self.export_dir}{base_name}.xlsx"
                r = xlsx_exp.to_excel(xls_path)
                if r and r not in generated_paths:
                    generated_paths.append(r)

        return generated_paths

    @staticmethod
    def _flatten(d: dict) -> dict:
        """Flatten a nested dict into one-level (strings only)."""
        flat = {}
        for k, v in d.items():
            if isinstance(v, dict):
                flat[k] = str({k2: str(v2) for k2, v2 in v.items()})
            else:
                flat[k] = str(v)
        return flat


def expose_to_json(data: Any, path: str) -> str:
    """Write any serializable data to a JSON file."""
    import json
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, default=str)
    return path


# --------------------------------------------------------------------------- #
#  Demo                                                                       #
# ---------------------------------------------------------------------------- #

def run_example():
    """Demo: generate an analytics report from sample data."""
    print("=" * 60)
    print("   Analytics Report Generator - Demo")
    print("=" * 60)

    # Sample sentiment output (simulating SentimentResult.to_dict())
    sample_sentiment = {
        "backend": "vader",
        "threshold": 0.1,
        "total_analyzed": 10,
        "per_subreddit": {
            "python": {
                "mean_score": 0.1627,
                "positive_count": 2,
                "neutral_count": 0,
                "negative_count": 1,
                "median_score": 0.3,
                "std_score": 0.85,
            },
            "datascience": {
                "mean_score": 0.1338,
                "positive_count": 2,
                "neutral_count": 0,
                "negative_count": 1,
                "median_score": -0.2,
                "std_score": 0.45,
            },
        },
    }

    sample_keywords = {
        "per_subreddit": {
            "python": {"keywords": [("type hints", 0.85), ("dataclasses", 0.72)]},
        },
        "global_keywords": [("transformers", 0.91), ("deep learning", 0.83)],
    }

    sample_topics = {
        "n_topics_modelled": 3,
        "coherence_score": 0.42,
        "perplexity": -0.67,
        "topic_labels": {0: "Python / Type Hints / Dataclasses", 1: "ML / Feature Engineering", 2: "React / Performance"},
        "topics_per_topic": {
            0: [("python", 0.34), ("typehints", 0.28), ("mypy", 0.21)],
            1: [("featureengineering", 0.31), ("transformers", 0.27), ("xgboost", 0.19)],
            2: [("reactquery", 0.35), ("swr", 0.29), ("performance", 0.22)],
        },
    }

    report = AnalyticsReport(
        title="Reddit Content Analytics Report",
        sentiment_data=sample_sentiment,
        keywords_data=sample_keywords,
        topics_data=sample_topics,
        export_dir="/tmp/",
    )

    paths = report.save("reddit_report", as_csv=True, as_excel=False, as_json=True)

    print(f"\\nGenerated {len(paths)} file(s):")
    for p in paths:
        path_path = Path(p)
        size = path_path.stat().st_size if path_path.exists() else 0
        print(f"  ✅ {p}  ({size:,} bytes)")

    # Print summary
    print("\\n📊 Report Summary:")
    print(f"   Title: {report.title}")
    print(f"   Generated: {report.generated_at}")
    print(f"   Sentiment subreddits: {list(report.sentiment_data.get('per_subreddit', {}).keys()) if report.sentiment_data else 'none'}")
    print(f"   Keywords subreddits: {list(report.keywords_data.get('per_subreddit', {}).keys()) if report.keywords_data else 'none'}")
    if report.topics_data:
        print(f"   Topics modelled: {report.topics_data.get('n_topics_modelled')}")


if __name__ == "__main__":
    run_example()
