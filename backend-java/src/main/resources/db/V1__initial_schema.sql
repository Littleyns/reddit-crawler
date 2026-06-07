-- V1: Initial schema for Reddit Crawler database
-- Replaces Hibernate ddl-auto=update with explicit Flyway migrations

CREATE TABLE IF NOT EXISTS crawler_job (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(255) UNIQUE NOT NULL,
    subreddit VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    posts_count INTEGER DEFAULT 0,
    comments_count INTEGER DEFAULT 0,
    crawl_params JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_crawler_job_status ON crawler_job(status);
CREATE INDEX IF NOT EXISTS idx_crawler_job_job_id ON crawler_job(job_id);
CREATE INDEX IF NOT EXISTS idx_crawler_job_subreddit ON crawler_job(subreddit);
CREATE INDEX IF NOT EXISTS idx_crawler_job_start_time ON crawler_job(start_time);

CREATE TABLE IF NOT EXISTS reddit_api_key_config (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    client_secret VARCHAR(255),
    alias VARCHAR(100),
    rotation_order INTEGER NOT NULL DEFAULT 0,
    access_token TEXT,
    refresh_token TEXT,
    token_expires_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_api_key_rotation_order ON reddit_api_key_config(rotation_order);
CREATE INDEX IF NOT EXISTS idx_api_key_active ON reddit_api_key_config(active);

CREATE TABLE IF NOT EXISTS metrics_snapshot (
    id BIGSERIAL PRIMARY KEY,
    subreddit VARCHAR(255),
    total_posts BIGINT DEFAULT 0,
    total_comments BIGINT DEFAULT 0,
    positive_count BIGINT DEFAULT 0,
    neutral_count BIGINT DEFAULT 0,
    negative_count BIGINT DEFAULT 0,
    sentiment_score DOUBLE PRECISION DEFAULT 0.0,
    daily_activity JSONB,
    keywords JSONB,
    insights JSONB,
    weekly_crawl JSONB,
    captured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_metrics_subreddit ON metrics_snapshot(subreddit);
CREATE INDEX IF NOT EXISTS idx_metrics_captured_at ON metrics_snapshot(captured_at);

CREATE TABLE IF NOT EXISTS idea_extraction (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    subtitle TEXT,
    category VARCHAR(50),
    confidence DOUBLE PRECISION,
    source_url VARCHAR(1000),
    subreddit VARCHAR(255),
    captured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_idea_category ON idea_extraction(category);
CREATE INDEX IF NOT EXISTS idx_idea_subreddit ON idea_extraction(subreddit);
CREATE INDEX IF NOT EXISTS idx_idea_captured_at ON idea_extraction(captured_at);

-- Insert default API key placeholder (active = false so crawler ignores until configured)
INSERT INTO reddit_api_key_config (client_id, client_secret, alias, rotation_order, active)
VALUES ('dev-placeholder', 'dev-placeholder', 'default', 0, false)
ON CONFLICT DO NOTHING;
