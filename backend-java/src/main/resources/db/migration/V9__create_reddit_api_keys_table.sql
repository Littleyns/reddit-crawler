-- V9: Create Reddit API key management table for multi-config support
CREATE TABLE IF NOT EXISTS reddit_api_keys (
    id SERIAL PRIMARY KEY,
    client_id VARCHAR(256) NOT NULL,
    client_secret VARCHAR(256) NOT NULL,
    alias VARCHAR(100),
    rotation_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    refresh_token VARCHAR(256),
    access_token VARCHAR(100),
    token_expires_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_api_key_alias ON reddit_api_keys(alias);
