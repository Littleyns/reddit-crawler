-- V11: Create metrics_snapshots table for MetricsSnapshot entity
-- This entity stores analytics snapshots (sentiment, keywords, additional metrics)
CREATE TABLE IF NOT EXISTS metrics_snapshots (
    id BIGSERIAL PRIMARY KEY,
    subreddit VARCHAR(100),
    snapshot_type VARCHAR(30) NOT NULL,
    mean_sentiment DOUBLE PRECISION,
    sentiment_distribution TEXT,  -- JSONB serialized as text for safety
    keyword_data TEXT,             -- JSONB serialized as text for safety
    additional_metrics TEXT,      -- JSONB serialized as text for safety
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes matching @Table(indexes) on MetricsSnapshot entity
CREATE INDEX IF NOT EXISTS idx_snapshot_subreddit ON metrics_snapshots(subreddit);
CREATE INDEX IF NOT EXISTS idx_snapshot_created_at ON metrics_snapshots(created_at);

COMMENT ON TABLE metrics_snapshots IS 'Stores analytics snapshots: sentiment analysis, keyword data, and additional metrics';
COMMENT ON COLUMN metrics_snapshots.snapshot_type IS 'Type of snapshot: SENTIMENT, KEYWORDS, AGGREGATE, etc.';
