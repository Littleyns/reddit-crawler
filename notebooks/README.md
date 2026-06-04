# DS Analyst — Notebooks & Analysis Scripts

Python NLP pipeline tools for the Reddit Crawler project.

## Modules

| Module | Description | Status |
|--------|-------------|--------|
| `keyphrase_extraction.py` | TF-IDF, KeyBERT, and Collocation keyword/phrase extraction per subreddit | ✅ Complete |

## Quick Start

```bash
cd /home/kali/projects/reddit-crawler-dev-agents/ds-analyst
source .venv/bin/activate

# Install dependencies
pip install keybert sentence-transformers   # for KeyBERT phrase extraction

# Run with default settings (uses all subreddits in DB)
python notebooks/keyphrase_extraction.py

# Analyze a single subreddit
python notebooks/keyphrase_extraction.py -s r/MachineLearning -l 200 -f json csv md

# Skip KeyBERT (faster, TF-IDF + bigrams only)
python notebooks/keyphrase_extraction.py --no-keybert
```

## CLI Reference

| Flag | Default | Description |
|------|---------|-------------|
| `--db` | `data/reddit_crawler.db` | SQLite database path |
| `-s, --subreddit` | all subreddits | Single subreddit name (e.g. r/MachineLearning) |
| `-l, --limit` | 500 | Max posts per subreddit |
| `-n, --top-n` | 20 | Top keywords/phrases to return |
| `-f, --format` | json csv | Output format(s): json, csv, md, all |
| `--no-keybert` | False | Skip KeyBERT (TF-IDF + bigrams only) |
| `-o, --output-dir` | output/keyphrase_reports | Output directory |

## Architecture

```
keyphrase_extraction.py
├── TextPreprocessor          → URL masking, username removal, lowercasing
├── TfidfExtractor            → Classical TF-IDF (no ML dependencies)
├── KeyBERTExtractor          → Neural keyphrases via SBERT embeddings
├── CollocationExtractor      → PMI-style bigram mining
├── RedditDBConnector         → SQLite bridge to crawler DB
├── ThemeReportBuilder        → Assembles multi-strategy theme reports
└── ReportFormatter           → JSON / CSV / Markdown output formats
```

## Output Formats

- **JSON** — per-subreddit report with all keyword lists and scores
- **CSV** — flat table: `subreddit, keyword, score, frequency, df, tfidf_score`
- **Markdown** — human-readable tables for each subreddit's top themes

## Dependencies

Core (pre-installed): numpy, pandas, scikit-learn, spacy, gensim, plotly, nltk
Optional: keybert, sentence-transformers (for neural phrase extraction)
