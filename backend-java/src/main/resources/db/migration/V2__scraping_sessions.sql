-- Flyway migration V2__scraping_sessions.sql

CREATE TABLE IF NOT EXISTS scraping_sessions (
    id BIGSERIAL PRIMARY KEY,
    subreddit VARCHAR(100) NOT NULL,
    sort VARCHAR(20) DEFAULT 'hot',
    post_limit INTEGER DEFAULT 25,
    depth INTEGER DEFAULT 1,
    include_comments BOOLEAN DEFAULT true,
    keywords VARCHAR(255),
    status VARCHAR(20) DEFAULT 'pending',
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    error_message TEXT
);

CREATE INDEX idx_scraping_sessions_subreddit ON scraping_sessions(subreddit);
CREATE INDEX idx_scraping_sessions_status ON scraping_sessions(status);
CREATE INDEX idx_scraping_sessions_started_at ON scraping_sessions(started_at);

COMMENT ON TABLE scraping_sessions IS 'Stores information about Reddit crawling sessions';
COMMENT ON COLUMN scraping_sessions.status IS 'Status of the crawl: pending, running, completed, failed, cancelled';
