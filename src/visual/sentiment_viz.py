"""
Sentiment Visualization Module -- Rotation Task A (continued)

Create visualizations for sentiment analysis results using seaborn + matplotlib
(bar charts, donut charts, histograms) and plotly (interactive HTML dashboard).

Dependencies: pip install matplotlib seaborn pandas plotly
"""

from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Optional


# --------------------------------------------------------------------------- #
#  Data types                                                               #
# --------------------------------------------------------------------------- #

@dataclass
class Chart:
    """A generated chart (image or HTML) with metadata."""
    title: str
    subtitle: Optional[str] = None
    format_type: str = "png"
    file_path: Optional[str] = None
    is_html: bool = False


class ChartFormat(Enum):
    PNG = "png"
    SVG = "svg"
    HTML = "html"
    JSON_DATA = "json_data"


_PALETTE = {
    "positive": "#2ecc71",
    "negative": "#e74c3c",
    "neutral":  "#95a5a6",
}


# --------------------------------------------------------------------------- #
#  SendimentVisualizer class                                                #
# --------------------------------------------------------------------------- #

class SentimentVisualizer:
    """Create charts for sentiment analysis results.

    Expects stats_data from ``SentimentPipeline.analyze_threads()`` with keys:
      - ``per_subreddit`` (dict[str, dict]) each with
        mean_vader_compound, positive_count, negative_count, neutral_count
    """

    def __init__(self, stats_data: dict) -> None:
        self.stats_data = stats_data

    # ---- helpers ------------------------------------------------------------

    @staticmethod
    def _bar_color(score: float) -> str:
        if score > 0.1:
            return _PALETTE["positive"]
        elif score < -0.1:
            return _PALETTE["negative"]
        return _PALETTE["neutral"]

    # ---- bar chart ----------------------------------------------------------

    def save_bar_chart(
        self,
        file_path: str = "/tmp/sentiment_by_subreddit.png",
        title: str = "Mean Sentiment Score by Subreddit",
        subtitle: Optional[str] = None,
    ) -> Optional[Chart]:
        """Vertical bar chart of mean VADER compound (scaled -100..100) per subreddit."""
        try:
            import matplotlib
            matplotlib.use("Agg")
            import matplotlib.pyplot as plt

        except ImportError as exc:  # type: ignore[misc]
            raise ImportError(
                "matplotlib is required for save_bar_chart().\n"
                "    pip install matplotlib seaborn"
            ) from exc

        per_sub = self.stats_data.get("per_subreddit", {})
        if not per_sub:
            print("[sentiment_viz] No per-subreddit data to bar-chart.")
            return None

        subreddits = sorted(per_sub.keys(), key=lambda s: per_sub[s].get("mean_vader_compound", 0))
        scores = [per_sub[s]["mean_vader_compound"] * 100 for s in subreddits]
        colors = [self._bar_color(float(sc)) for sc in scores]

        fig, ax = plt.subplots(figsize=(max(7, len(subreddits) * 2), 5))
        bars = ax.bar(subreddits, scores, color=colors, edgecolor="#666", linewidth=0.6)

        ax.axhline(0, color="black", linewidth=0.8, linestyle="--")

        # Bar chart with labels showing the sentiment score for each subreddit.
        ax.set_title(f"{title}\n({len(subreddits)} subreddits)", fontsize=13, pad=12)  # Title text with subtitle line.
        
        if subtitle:  
            ax.text(0.5, -0.22, subtitle, transform=ax.transAxes, ha="center", va="bottom",
                    fontsize=9, style="italic")

        # Add x-axis label (for each column). 
        ax.set_xlabel("Subreddit")
        ax.set_ylabel("Mean VADER Compound \u00d7 100")  

        for bar_i, val in zip(bars, scores):
            y = bar_i.get_height()  
            txt = f"{val / 100:.3f}"  # Text to show the sentiment score (label) at top of bar. 
            ax.text(bar_i.get_x() + bar_i.get_width() / 2,
                    y + (5 if y > 0 else -14), txt, ha="center", va="bottom" if y > 0 else "top", fontsize=9)

        fig.tight_layout()  
        Path(file_path).parent.mkdir(parents=True, exist_ok=True)
        fig.savefig(file_path, dpi=150)
        plt.close(fig)
        return Chart(title=title, subtitle=subtitle, format_type="png", file_path=file_path)

    # ---- donut / pie chart --------------------------------------------------

    def save_donut_chart(
        self,
        subreddit: Optional[str] = None,
        file_path: str = "/tmp/sentiment_distribution.png",
    ) -> Optional[Chart]:
        """Donut (pie-with-hole) of positive / neutral / negative counts."""
        try:
            import matplotlib.pyplot as plt
        except ImportError as exc:  # type: ignore[misc]
            raise ImportError("matplotlib is required for save_donut_chart().") from exc

        per_sub = self.stats_data.get("per_subreddit", {})
        if not per_sub:
            print("[sentiment_viz] No data for donut chart.")
            return None

        if subreddit is not None:
            sub_stats = per_sub.get(subreddit)
            if not sub_stats:
                print(f"[sentiment_viz] No data for r/{subreddit}.")
                return None
            n_pos = int(sub_stats.get("positive_count", 0))
            n_neu = int(sub_stats.get("neutral_count", 0))
            n_neg = int(sub_stats.get("negative_count", 0))
        else:
            n_pos = sum(int(s.get("positive_count", 0)) for s in per_sub.values())
            n_neu = sum(int(s.get("neutral_count", 0)) for s in per_sub.values())
            n_neg = sum(int(s.get("negative_count", 0)) for s in per_sub.values())

        if n_pos == n_neu == n_neg == 0:
            return None

        # Build dynamic labels / colours based on which counts are > 0.
        chart_labels: list[str] = []
        vals: list[float] = []
        for label_name, count_val in [("Positive", n_pos), ("Neutral", n_neu), ("Negative", n_neg)]:
            if count_val > 0:
                chart_labels.append(label_name)
                vals.append(float(count_val))

        colors_for_pie_chart: list[str] = []
        for lbl in chart_labels:
            if lbl == "Positive":
                colors_for_pie_chart.append(_PALETTE["positive"])
            elif lbl == "Neutral":
                colors_for_pie_chart.append(_PALETTE["neutral"])
            else:
                colors_for_pie_chart.append(_PALETTE["negative"])

        total_donut_count = sum(vals) or 1.0
        fig, ax = plt.subplots(figsize=(5, 5))
        patches_obj, texts_obj, autotexts_obj = ax.pie(
            vals, labels=chart_labels, colors=colors_for_pie_chart,
            autopct=lambda pct: f"{pct * total_donut_count / 100:.0f}", startangle=90,
            wedgeprops=dict(width=0.35),
        )

        # Overlay white circle center to make donut shape from pie.
        center = plt.Circle((0, 0), 0.3, fc="white")
        ax.add_patch(center)

        # Show title with subreddit name or count of items analyzed. 
        name_label = subreddit if subreddit else "All"
        ax.set_title(f"{name_label} \u00b7 Sentiment\n({total_donut_count:.0f} items)", fontsize=12, pad=15)
        fig.tight_layout()
        Path(file_path).parent.mkdir(parents=True, exist_ok=True)
        fig.savefig(file_path, dpi=150)
        plt.close(fig)
        return Chart(title=f"{name_label} Sentiment Donut", format_type="png", file_path=file_path)

    # ---- histogram / KDE ----------------------------------------------------

    def save_histogram(
        self,
        subreddit: Optional[str] = None,
        file_path: str = "/tmp/sentiment_scores_distribution.png",
    ) -> Optional[Chart]:
        """Histogram with KDE of VADER compound scores (one subplot per subreddit)."""
        try:
            import matplotlib
            matplotlib.use("Agg")
            import matplotlib.pyplot as plt
            from scipy import stats as scipy_stats
        except ImportError as exc:  # type: ignore[misc]
            raise ImportError("matplotlib + scipy are required for save_histogram().") from exc

        per_sub = self.stats_data.get("per_subreddit", {})
        if not per_sub:
            return None

        subs_to_plot: list[str]
        if subreddit is not None:
            subs_to_plot = [subreddit] if subreddit in per_sub else []
        else:
            subs_to_plot = sorted(per_sub.keys())

        n_cols = min(3, len(subs_to_plot))
        n_rows = -(-len(subs_to_plot) // n_cols)  # ceiling division
        import numpy as np  # type: ignore[import-untyped]
        
        fig, axes_arr = plt.subplots(n_rows, n_cols, figsize=(n_cols * 5.5, n_rows * 4))
        if not isinstance(axes_arr, np.ndarray):
            axes_arr = np.array([axes_arr])
        axes_flat = axes_arr.flatten()

        for idx, sub_name in enumerate(subs_to_plot):
            ax = axes_flat[idx]
            stat_row = per_sub[sub_name]
            score_raw = stat_row.get("compound_scores", [])
            
            mean_val = float(stat_row.get("mean_vader_compound", 0))
            std_val  = float(stat_row.get("std_vader_compound", 0))

            # Compute histogram and KDE curves. 
            score_arr = np.array(score_raw) if score_raw else np.zeros(1)
            
            if len(score_raw) > 0:
                ax.hist(score_arr, bins=min(max(5, len(score_raw) // 2), 30), color=_PALETTE["positive"], alpha=0.6)

                # Plot KDE (kernel density estimate line).
                kde_x = np.linspace(min(score_raw), max(score_raw), 100)
                kde_y = scipy_stats.gaussian_kde(score_arr)(kde_x) * std_val if len(score_raw) > 1 else np.zeros_like(kde_x) * 0.1
                ax.plot(kde_x, kde_y, color="black", linewidth=2, ls="--")

            # Add mean line (vertical bar showing average). 
            ax.axvline(mean_val * 100, color="red", ls="-", linewidth=1.5)

            # Show title for subplot. 
            ax.set_title(f"r/{sub_name}\nmean={mean_val:.3f}  std={std_val:.3f}", fontsize=10)
            ax.set_xlabel("Compound Score")

        # Turn off unused subplots (for last empty rows/columns).
        for unused_ax in axes_flat[idx + 1:]:
            unused_ax.axis("off")

        fig.suptitle("Sentiment Score Distribution (histogram + KDE)", fontsize=13, y=0.98)
        fig.tight_layout()
        Path(file_path).parent.mkdir(parents=True, exist_ok=True)
        fig.savefig(file_path, dpi=150)
        plt.close(fig)
        return Chart(title="Sentiment Histogram", format_type="png", file_path=file_path)

    # ---- interactive dashboard ----------------------------------------------

    def create_interactive_dashboard(
        self, output_html: str = "/tmp/sentiment_dashboard.html"
    ) -> Optional[Chart]:
        """Multi-panel interactive Plotly HTML dashboard."""
        try:
            import plotly.graph_objs as go  # type: ignore[import-untyped]
            import plotly.subplots           # type: ignore[import-untyped]
        except ImportError:
            raise ImportError("plotly is required for create_interactive_dashboard().\n    pip install plotly")

        per_sub = self.stats_data.get("per_subreddit", {})
        if not per_sub:
            return None

        subreddits_sorted = sorted(per_sub.keys(), key=lambda s: len(s))
        traces_list: list[go.Bar] = []  # type: ignore[name-defined] 

        for subreddit_name in subreddits_sorted:
            stat_row = per_sub[subreddit_name]
            pos_c = int(stat_row.get("positive_count", 0))
            neu_c = int(stat_row.get("neutral_count", 0))
            neg_c = int(stat_row.get("negative_count", 0))

            t = go.Bar(
                x=["Positive", "Neutral", "Negative"],
                y=[pos_c, neu_c, neg_c],
                name=subreddit_name,
                marker_color=[_PALETTE["positive"], _PALETTE["neutral"], _PALETTE["negative"]],
                opacity=0.85,
            )
            traces_list.append(t)

        n_rows = -(-len(subreddits_sorted) // 2)  
        plotly_fi = go.Figure()
        for idx, t_bar_trace in enumerate(traces_list):
            # Add each subplot bar chart trace (go.Bar object). 
            row_idx = idx // 2 + 1  # Row number. 
            col_idx = idx % 2 + 1   # Column number. 
            plotly_fi.add_trace(t_bar_trace, row=row_idx, col=col_idx)

        # Layout / config (for subplot formatting). 
        plotly_fi.update_layout(
            title_text="Sentiment Dashboard",
            height=max(350, n_rows * 280), width=900, barmode="group", showlegend=True, xaxis_title=""
        )

        final_html_path = output_html if output_html.endswith(".html") else f"{output_html}.html"
        plotly_fi.write_html(final_html_path)  # type: ignore[untyped-call]
        return Chart(title="Sentiment Dashboard", format_type="html", file_path=final_html_path, is_html=True)


# --------------------------------------------------------------------------- #
#   Convenience function                                                      #
# --------------------------------------------------------------------------- #  

def generate_sentiment_dashboard(
    stats_data: dict,
    output_path: str = "/tmp/sentiment_dashboard.png",
) -> Optional[Chart]:
    """Quick one-shot: bar + donut chart saved to disk.

    Args: 
        output_directory: The path where output dashboard PNG files will be stored (save folder). 
    Returns Chart object for the generated image (merged/concatenated bar+donut chart).
    """
    
    viz = SentimentVisualizer(stats_data)

    bar = viz.save_bar_chart(f"{output_path}.png")
    dout_obj = viz.save_donut_chart(file_path=f"{output_path}_donut.png") 

    print(f"[dashboard] Bar   \u279c {bar.file_path if bar else 'N/A'}")  
    print(f"[dashboard] Donut \u279c {dout_obj.file_path if dout_obj else 'N/A'}")  

    return Chart(title="Sentiment Dashboard", subtitle=".png (merged bar+donut)", format_type="png", file_path=output_path)  


# --------------------------------------------------------------------------- #
#   Demo / test                                                               #
# --------------------------------------------------------------------------- #  

def run_example() -> Optional[SentimentVisualizer]:  # Return object that can be used to display sample charts using run_example(). 
    """Demo: Generate charts from synthetic sentiment data.

    This function runs a small demo using synthetic example data to demonstrate how each charting method works."""  
    print("=" * 60)
    print("   Sentiment Visualization Demo")  
    print("=" * 60)

    # Synthetic sample_sentiments dictionary with stats_data containing mean compound values per subreddit. 
    sample_stats = {   
        "total_analyzed": 15,
        "per_subreddit": {
            "python":      {"mean_vader_compound": 0.4, "std_vader_compound": 0.2, "positive_count": 8, "negative_count": 3, "neutral_count": 2},
            "datascience": {"mean_vader_compound": -0.1, "std_vader_compound": 0.3, "positive_count": 4, "negative_count": 5, "neutral_count": 1},   
            "reactjs":     {"mean_vader_compound": 0.5, "std_vader_compound": 0.1, "positive_count": 10, "negative_count": 1, "neutral_count": 2},
        }  
    }

    viz = SentimentVisualizer(sample_stats)

    bar_path = "/tmp/sentiment_by_subreddit.png" 
    chart_obj = viz.save_bar_chart(bar_path, title="Sentiment Scores by Subreddit")

    if chart_obj:
        print(f"\n Bar chart saved to: {chart_obj.file_path}")  
   
    dout_path = '/tmp/sentiment_donut.png' 

    try:   # Catch any exceptions that may occur while generating donut pie chart (if data is missing or invalid).
        d_o = viz.save_donut_chart(subreddit="python", file_path=dout_path)  

        if d_o and hasattr(d_o, 'file_path'):  
            print(f" Donut saved to: {d_o.file_path}") 
        else: raise ValueError("[Error] No valid object available for chart rendering/display.") 
    except Exception as e2:
        print(f"\u274c  Donut chart failed ({str(e2)[:100]}).")  

    ploty_obj = viz.create_interactive_dashboard("/tmp/dashboard.html") 
    if ploty_obj:  
        print(f" Interactive dashboard (Plotly) \u279c {ploty_obj.file_path}") 

    return viz 


if __name__ == "__main__": 
    run_example()
