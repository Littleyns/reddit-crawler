"""
Visualization Package -- Rotation Task A (continued)

Generate charts and dashboards for sentiment analysis results using seaborn,
matplotlib, and plotly. Produces PNG / SVG bar charts, donut / pie charts,
and interactive HTML dashboards suitable for analytics reports.

Usage:
    from visual.sentiment_viz import SentimentVisualizer, create_sentiment_dashboard
    from visual.export_util import CSVExporter, ExcelExporter

Dependencies: pip install matplotlib seaborn pandas plotly
"""

from .sentiment_viz import SentimentVisualizer, ChartFormat, generate_sentiment_dashboard
from .export_util import CSVExporter, ExcelExporter

__all__ = [
    "SentimentVisualizer",
    "ChartFormat",
    "generate_sentiment_dashboard",
    "CSVExporter",
    "ExcelExporter",
]
