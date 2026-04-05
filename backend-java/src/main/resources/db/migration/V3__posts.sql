-- Flyway migration V3__posts.sql

CREATE TABLE IF NOT EXISTS posts (
    id BIGSERIAL PRIMARY KEY,
    reddit_id VARCHAR(20) NOT NULL UNIQUE,
    scraping_session_id BIGINT NOT NULL REFERENCES scraping_sessions(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    author VARCHAR(100) DEFAULT '[deleted]',
    subreddit VARCHAR(100) NOT NULL,
    upvotes INTEGER DEFAULT 0,
    url VARCHAR(1000),
    content TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_posts_reddit_id ON posts(reddit_id);
CREATE INDEX idx_posts_scraping_session_id ON posts(scraping_session_id);
CREATE INDEX idx_posts_subreddit ON posts(subreddit);
CREATE INDEX idx_posts_created_at ON posts(created_at);

CREATE INDEX idx_posts_session_subreddit ON posts(scraping_session_id, subreddit);

COMMENT ON TABLE posts IS 'Stores Reddit posts collected during crawling sessions';
COMMENT ON COLUMN posts.reddit_id IS 'Unique Reddit identifier for deduplication';
COMMENT ON COLUMN posts.scraping_session_id IS 'Foreign key to the crawling session that collected this post';
