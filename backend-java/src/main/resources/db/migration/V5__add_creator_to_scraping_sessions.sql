-- Flyway migration V5__add_creator_to_scraping_sessions.sql

ALTER TABLE scraping_sessions
ADD COLUMN IF NOT EXISTS creator_id BIGINT REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_scraping_sessions_creator_id ON scraping_sessions(creator_id);

COMMENT ON COLUMN scraping_sessions.creator_id IS 'User who created the crawl session';
