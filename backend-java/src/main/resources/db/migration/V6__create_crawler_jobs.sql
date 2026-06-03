-- V6__create_crawler_jobs.sql
-- Creates table expected by @Entity CrawlerJob (javax.persistence/Jakarta)

CREATE TABLE IF NOT EXISTS crawler_jobs (
    id          BIGSERIAL PRIMARY KEY,
    job_id      VARCHAR(255)  NOT NULL UNIQUE,
    subreddit   VARCHAR(256)  NOT NULL,
    status      VARCHAR(32)   NOT NULL,
    query_json  TEXT,               -- arbitrary JSON parameters used at launch
    result_json TEXT,               -- scraped results stored as JSON
    started_at  TIMESTAMP WITHOUT TIME ZONE,
    completed_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_crawler_jobs_status ON crawler_jobs (status);
