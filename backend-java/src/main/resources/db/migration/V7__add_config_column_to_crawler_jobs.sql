-- ============================================================================
-- V7: Add optional JSONB configuration column to crawler_jobs table
-- Week W23: enables per-job crawl parameters (depth, timeWindow, filters)
-- ============================================================================

-- Add config column with safe defaults if it doesn't already exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'crawler_jobs' 
        AND column_name = 'config'
    ) THEN
        ALTER TABLE crawler_jobs ADD COLUMN config JSONB DEFAULT '{}';
        
        -- Create GIN index for efficient JSONB queries
        CREATE INDEX IF NOT EXISTS idx_crawler_jobs_config ON crawler_jobs USING gin(config);
        
        RAISE NOTICE 'V7: Added config JSONB column to crawler_jobs (GIN indexed)';
    ELSE
        RAISE NOTICE 'V7: config column already exists on crawler_jobs — skipping';
    END IF;
END $$;
