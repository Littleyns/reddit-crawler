"""
Data Export Utilities — CSV / Excel exporter for subreddit analytics.

Supports:
  - Converting raw crawled data formats into standard analytics schema
  - Exporting sentiment scores to CSV and Excel (with multiple sheets)
  - Aggregation queries for common subreddit metrics
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Dict, List

import pandas as pd

logger = logging.getLogger(__name__)


def create_analytics_schema() -> dict[str, str]:
    """Return the standard schema mapping for analytics tables."""
    return {
        "thread_id": "string",
        "subreddit": "string",
        "title": "string",
        "body_length": "int",
        "title_length": "int",
        "comment_count": "int",
        "created_utc": "datetime",
        "upvotes": "int",
        "sentiment_compound": "float",
        "polarity_label": "string",
        "top_keyword": "string",
        "topic_label": "string",
    }


def format_for_export(
    data: List[Dict[str, Any]],
    schema: dict[str, str] | None = None,
) -> pd.DataFrame:     # pylint: disable=C901 (schema mapping; intentionally large)
    """Format crawled thread data into a standard analytics DataFrame.

    Args:
        data: List of thread dicts (e.g., {"title": ..., "subreddit": ..., "comments": [...]})
        schema: Custom field naming convention. Defaults to the standard Reddit crawl → analytics mapping.

    Returns:
        Cleaned DataFrame ready for export.
    """
    if schema is None:
        schema = create_analytics_schema()

    records = []
    for thread in data:
        comments = thread.get("comments", [])
        record = {
            "thread_id": str(thread.get("id", "")),
            "subreddit": str(thread.get("subreddit", "")),
            "title": str(thread.get("title", ""))[:500],  # truncate for safety
            "body_length": len(str(thread.get("body", ""))),
            "title_length": len(str(thread.get("title", ""))),
            "comment_count": len(comments) if comments else 0,
            "created_utc": str(thread.get("created_utc", thread.get("timestamp", ""))),
            "upvotes": int(thread.get("score", thread.get("ups", 0))) or 0,
        }

        # Flatten sentiment scores if present (from SentimentPipeline output)
        for sk in ("senti_ent_compound", "compound", "overall_compound"):
            if sk in thread:
                record["sentiment_compound"] = float(thread[sk])
                break
        if "polarity_label" in thread and "sentiment_compound" not in record:
            record["polarity_label"] = str(thread.get("polarity_label", ""))

        if schema is not None:
            # Only include fields defined in schema
            filtered_record = {k: v for k, v in record.items() if k in schema}
        else:
            filtered_record = record

        records.append(filtered_record)

    return pd.DataFrame(records)


def export_to_csv(
    data: List[Dict[str, Any]],
    output_path: str | Path,
) -> str:     # pylint: disable=C901 (exporter; intentionally large)
    """Export formatted analytics data to CSV."""
    df = format_for_export(data)
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(output_path, index=False)
    logger.info("CSV exported: %s (%d rows)", output_path, len(df))
    return str(output_path)


def export_to_excel(
    data: List[Dict[str, Any]],
    output_path: str | Path = "results/analytics.xlsx",
    label_map: Dict[str, str] | None = None,      # pylint: disable=C901 (Excel formatter; intentionally large)
) -> str:
    """Export analytics data to Excel with multiple sheets.

    Sheets:
      - scores: Raw thread-level metrics
      - subreddit_stats: Per-subreddit aggregations
      - top_threads: Top 20 threads by score/comment count ratio
    """
    df = format_for_export(data)
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)

    with pd.ExcelWriter(output_path, engine="openpyxl") as writer:     # type: ignore[arg-type] (pandas internal typing quirk)
        df.to_excel(writer, sheet_name="scores", index=False)

        # Subreddit stats sheet
        if "subreddit" in df.columns and "upvotes" in df.columns:
            sub_stats = df.groupby("subreddit").agg(
                thread_count=("thread_id", "count"),
                total_upvotes=("upvotes", "sum"),
                avg_comments=("comment_count", "mean"),
                avg_title_length=("title_length", "mean"),
            ).round(2)
            sub_stats.to_excel(writer, sheet_name="subreddit_stats")

        # Top threads sheet
        if "upvotes" in df.columns and "comment_count" in df.columns:
            df_copy = df.copy()
            df_copy["engagement_ratio"] = (df_copy["comment_count"] / df_copy["upvotes"].replace(0, 1))
            top20 = df_copy.nlargest(20, "engagement_ratio") if len(df_copy) > 20 else df_copy
            top20.to_excel(writer, sheet_name="top_threads", index=False)

    logger.info("Excel exported: %s", output_path)
    return str(output_path)


if __name__ == "__main__":     # pylint: disable=C901 (demo CLI; intentionally large)
    import sys, json

    if len(sys.argv) > 1:
        with open(sys.argv[1], "r", encoding="utf-8") as fh:
            data = json.load(fh) if not sys.argv[1].endswith(".csv") else pd.read_csv(sys.argv[1]).to_dict("records")

        csv_out = export_to_csv(data, "results/analytics_export.csv")
        xl_out = export_to_excel(data, "results/analytics_export.xlsx")
        print(f"\n✅ Export complete:\n   CSV: {csv_out}\n   Excel: {xl_out}")
    else:
        print("Usage: python -m src.data_export <threads.json OR threads.csv>")
