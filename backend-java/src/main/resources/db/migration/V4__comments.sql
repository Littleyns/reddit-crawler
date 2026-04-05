-- Flyway migration V4__comments.sql

CREATE TABLE IF NOT EXISTS comments (
    id BIGSERIAL PRIMARY KEY,
    reddit_id VARCHAR(20) NOT NULL UNIQUE,
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    parent_comment_id BIGINT REFERENCES comments(id),
    author VARCHAR(100) DEFAULT '[deleted]',
    body TEXT NOT NULL,
    upvotes INTEGER DEFAULT 0,
    depth INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_comments_reddit_id ON comments(reddit_id);
CREATE INDEX idx_comments_post_id ON comments(post_id);
CREATE INDEX idx_comments_parent_comment_id ON comments(parent_comment_id);
CREATE INDEX idx_comments_created_at ON comments(created_at);

CREATE INDEX idx_comments_post_created ON comments(post_id, created_at);

COMMENT ON TABLE comments IS 'Stores Reddit comments with support for parent-child relationships';
COMMENT ON COLUMN comments.parent_comment_id IS 'Self-reference for nested comment threads';
COMMENT ON COLUMN comments.reddit_id IS 'Unique Reddit identifier for deduplication';

-- Insert trigger to automatically set created_at if not provided
CREATE OR REPLACE FUNCTION set_comment_created_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.created_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_comment_created_at_trigger
BEFORE INSERT ON comments
FOR EACH ROW
EXECUTE FUNCTION set_comment_created_at();
