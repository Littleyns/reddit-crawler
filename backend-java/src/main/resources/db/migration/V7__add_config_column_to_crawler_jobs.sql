-- V7: Add the missing 'config' column to crawler_jobs table.
-- The CrawlerJob entity defines a `String config` field but no prior migration
-- creates it, causing Hibernate schema-validation failure on startup.

ALTER TABLE crawler_jobs ADD COLUMN IF NOT EXISTS config JSONB;

COMMENT ON COLUMN crawler_jobs.config IS 'Crawler job configuration payload (JSON)';
