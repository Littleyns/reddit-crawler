"""
Visualization Module — Reddit Analytics Insights

Scaffold for analyzing and visualizing crawling insights, including:
  - Sentiment score distributions
  - Subreddit comparison dashboards  
  - Temporal activity trends (if timestamps available)
  - Engagement metrics (comment-to-title ratios, etc.)

Dependencies: pandas >= 2.0, matplotlib, seaborn, plotly
Note: keybert and sklearn for keyword analysis live in keyword_extractor.py
      gensim for topic modeling lives in topic_modeler.py
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Dict, List, Optional

import pandas as pd

logger = logging.getLogger(__name__)


def load_sentiment_scores(csv_path: str | Path) -> pd.DataFrame:
    """Load sentiment scores CSV into a DataFrame."""
    df = pd.read_csv(csv_path)
    logger.info("Loaded %d sentiment records from %s", len(df), csv_path)
    return df


def plot_distribution(
    df: pd.DataFrame,
    column: str = "overall_compound",
    title: str = "Sentiment Score Distribution",
    output_path: Optional[str | Path] = None,
):     # pylint: disable=C901 (visualization; intentionally large)
    """Plot histogram of compound scores with KDE, saved to PNG."""
    import matplotlib
    matplotlib.use("Agg")  # non-interactive backend
    import matplotlib.pyplot as plt
    import seaborn as sns

    fig, axes = plt.subplots(1, 2, figsize=(14, 5))

    # Histogram + KDE
    sns.histplot(df[column], kde=True, bins=30, ax=axes[0], color="#4C72B0")
    axes[0].set_title(title)
    axes[0].axvline(x=df[column].mean(), color="red", linestyle="--", label=f"Mean: {df[column].mean():.3f}")
    axes[0].legend()

    # Bar chart of class counts
    counts = df["polarity_label"].value_counts().reindex(["positive", "neutral", "negative"], fill_value=0)
    palette = {"positive": "#55A868", "neutral": "#949494", "negative": "#C44E52"}
    sns.barplot(x=counts.index, y=counts.values, ax=axes[1], palette=[palette.get(c, "#CCC") for c in counts.index])
    axes[1].set_title("Polarity Class Counts")

    plt.tight_layout()

    out = output_path or "results/sentiment/dist.png"
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out, dpi=150)
    plt.close(fig)
    logger.info("Distribution plot saved to %s", out)
    return out


def subreddit_comparison(df: pd.DataFrame) -> pd.DataFrame:     # pylint: disable=C901 (visualization helper; intentionally large)
    """Compute and return per-subreddit comparison stats."""
    grouped = df.groupby("subreddit")["overall_compound"].agg(["mean", "std", "count", "min", "max"])
    grouped.columns = ["avg_compound", "std_compound", "thread_count", "min_score", "max_score"]
    return grouped.round(4).sort_values("avg_compound", ascending=False)


def create_plotly_dashboard(
    df: pd.DataFrame,
    output_path: str | Path = "results/sentiment/dashboard.html",
) -> str:     # pylint: disable=C901 (plotly chart generation; intentionally large)
    """Create an interactive Plotly HTML dashboard and save it.

    Panels:
      1. Sentiment score histogram
      2. Polarity pie chart
      3. Subreddit comparison bar chart
    """
    import plotly.graph_objects as go
    from plotly.subplots import make_subplots

    fig = make_subplots(
        rows=2, cols=2,
        subplot_titles=(
            "Sentiment Scores", "Polarity Distribution",
            "Subreddit Comparison", "Score by Polarity Class",
        ),
        specs=[
            [{"type": "histogram"}, {"type": "pie"}],
            [{"type": "bar"}, {"type": "box"}],
        ],
    )

    # 1. Histogram (row 1, col 1)
    fig.add_trace(
        go.Histogram(x=df["overall_compound"], nbinsx=30, marker_color="#4C72B0", name="histogram"),
        row=1, col=1,
    )

    # 2. Pie chart (row 1, col 2)
    counts = df["polarity_label"].value_counts()
    fig.add_trace(
        go.Pie(
            labels=counts.index.tolist(),
            values=counts.values.tolist(),
            name="pie",
            pull=[0.05 if l == max(counts, key=lambda x: counts[x]) else 0 for l in counts.index],
        ),
        row=1, col=2,
    )

    # 3. Subreddit comparison (row 2, col 1)
    sub_stats = subreddit_comparison(df)
    fig.add_trace(
        go.Bar(
            x=sub_stats.index.tolist(),
            y=sub_stats["avg_compound"].tolist(),
            marker_color="#55A868",
            name="subreddit",
        ),
        row=2, col=1,
    )

    # 4. Box plot by class (row 2, col 2)
    fig.add_trace(
        go.Box(y=df.loc[df["polarity_label"] == "positive", "overall_compound"], name="Positive"),
        row=2, col=2,
    )
    fig.add_trace(
        go.Box(y=df.loc[df["polarity_label"] == "neutral", "overall_compound"], name="Neutral"),
        row=2, col=2,
    )
    fig.add_trace(
        go.Box(y=df.loc[df["polarity_label"] == "negative", "overall_compound"], name="Negative"),
        row=2, col=2,
    )

    fig.update_layout(height=700, title_text="Reddit Sentiment Analysis Dashboard", showlegend=False)

    out_path = Path(output_path) if isinstance(output_path, str) else output_path
    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.write_html(str(out_path))
    logger.info("Interactive dashboard saved to %s", out_path)
    return str(out_path)


if __name__ == "__main__":     # pylint: disable=C901 (demo script; intentionally large)
    import sys, os

    if len(sys.argv) > 1:
        csv_input = sys.argv[1]
        df = load_sentiment_scores(csv_input)
        dist_path = plot_distribution(df)
        dashboard_path = create_plotly_dashboard(df)
        comp_df = subreddit_comparison(df)
        
        print(f"\n✅ Analytics complete!")
        print(f"   Distribution: {dist_path}")
        print(f"   Dashboard: {dashboard_path}")
        print(f"   Subreddit comparison:\n{comp_df.to_string()}")
    else:
        print("Usage: python -m src.visualization <sentiment_scores.csv>")
