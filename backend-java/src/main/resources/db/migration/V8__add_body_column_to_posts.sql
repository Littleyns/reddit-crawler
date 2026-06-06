-- V8: Add the missing 'body' column to posts table.
-- The Post.java entity defines a String body field but no prior migration creates it,
-- causing Hibernate schema-validation failure on startup.
-- The 'content' column from V3 is deprecated; use 'body' going forward.

ALTER TABLE posts ADD COLUMN IF NOT EXISTS body TEXT
  GENERATED ALWAYS AS (content) STORED;

COMMENT ON COLUMN posts.body IS 'Post text body - generated from content field';

-- Migrate existing data: ensure any NULL content rows have consistent body
UPDATE posts SET body = content WHERE body IS NULL AND content IS NOT NULL;
