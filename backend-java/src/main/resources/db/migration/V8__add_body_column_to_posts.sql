-- V8: Add the 'body' column to posts table if missing.
-- The 'content' column is the actual body content (Per Post.java mapping).
-- This migration is idempotent and safe for production databases.

ALTER TABLE posts ADD COLUMN IF NOT EXISTS body TEXT;

COMMENT ON COLUMN posts.body IS 'Post text body - alias to content field';
