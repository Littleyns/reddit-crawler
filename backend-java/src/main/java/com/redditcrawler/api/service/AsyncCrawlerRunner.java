package com.redditcrawler.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redditcrawler.api.model.CrawlerJob;
import com.redditcrawler.api.model.RedditApiKeyConfig;
import com.redditcrawler.api.repository.CrawlerJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * P1-2: Async crawler runner with @Scheduled periodic health/status checks.
 * P4-1: Integrated RedditApiRotationService for round-robin API key rotation.
 * 
 * This service performs three roles:
 * 1) Schedules crawl jobs asynchronously via @Async on the crawlExecutor pool.
 * 2) Runs a scheduled health-sweep every 30 seconds to detect and mark stale RUNNING jobs.
 * 3) Reports active crawler count every 60 seconds for monitoring/logging.
 */
@Service
public class AsyncCrawlerRunner {

    private static final Logger log = LoggerFactory.getLogger(AsyncCrawlerRunner.class);

    private final RedditCrawlerService crawlerService;
    private final CrawlerJobRepository jobRepo;
    private final RedditApiRotationService rotationService;  // P4-1: dependency injection
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public AsyncCrawlerRunner(RedditCrawlerService crawlerService, 
                              CrawlerJobRepository jobRepo,
                              RedditApiRotationService rotationService) {  // P4-1: added param
        this.crawlerService = crawlerService;
        this.jobRepo = jobRepo;
        this.rotationService = rotationService;  // P4-1: inject
    }

    /* ──────────────────────────────────
     * @Scheduled tasks
     * ────────────────────────────────── */

    /**
     * Health sweep every 30 seconds. Finds all RUNNING jobs older than 60 minutes and marks them FAILED_STALE.
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void healthSweep() {
        if (!initialized.get()) {
            log.info("[SCHEDULER] AsyncCrawlerRunner starting health-sweep with P4-1 rotation active");
            initialized.set(true);
            
            // Log current API key availability from the rotation service (P4-1)
            int validKeys = rotationService.getActiveKeyCount();
            log.info("[ROTATION] {} valid active API keys available for crawling", validKeys);
        }

        long thresholdMinutes = 60;
        Instant deadline = Instant.now().minusSeconds(thresholdMinutes * 60);
        
        try {
            List<CrawlerJob> staleJobs = jobRepo.findStaleJobs(deadline);
            
            if (!staleJobs.isEmpty()) {
                log.warn("[SCHEDULER] Found {} stale RUNNING jobs (older than {} min). Marking FAILED_STALE.", 
                         staleJobs.size(), thresholdMinutes);
                
                for (CrawlerJob job : staleJobs) {
                    String jobId = job.getJobId();

                    // Stop the crawl first
                    try {
                        crawlerService.stopCrawl(jobId);
                    } catch (Exception e) {
                        log.warn("[SCHEDULER] Could not stop stale job {}: {}", jobId, e.getMessage());
                    }

                    // Mark as FAILED_STALE in JPA repo — find the job again since entity graph may have changed
                    for (CrawlerJob j : jobRepo.findByStatus("RUNNING")) {
                        if (j.getJobId().equals(jobId)) {
                            j.setStatus("FAILED_STALE");
                            try {
                                jobRepo.save(j);
                                log.info("[SCHEDULER] Saved FAILED_STALE status to JPA for jobId={}", jobId);
                            } catch (Exception e) {
                                log.warn("[SCHEDULER] Could not save status for {}: {}", jobId, e.getMessage());
                            }
                        }
                    }

                    // In-memory / Redis fallback: update status in crawler service cache
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> allJobs = (List<Map<String, Object>>) crawlerService.getAllJobs();
                    for (Map<String, Object> j : allJobs) {
                        if (j.get("jobId").toString().equals(jobId)) {
                            j.put("status", "FAILED_STALE");
                            break;
                        }
                    }

                    log.info("[SCHEDULER] Marked job {} as FAILED_STALE", jobId);
                }
            } else {
                log.debug("[SCHEDULER] Health sweep complete — no stale jobs found.");
            }
        } catch (Exception e) {
            log.error("[SCHEDULER] Health-sweep error", e);
        }

        // P4-1: periodically check token expiry and auto-refresh if needed
        try {
            List<RedditApiKeyConfig> keys = rotationService.getAllActiveKeys();
            for (RedditApiKeyConfig key : keys) {
                if (rotationService.needsTokenRefresh(key)) {
                    log.info("[ROTATION] Auto-refreshing expired/soon-expiring token for alias '{}'", key.getAlias());
                    refreshTokenSilently(key);
                }
            }
        } catch (Exception e) {
            log.warn("[ROTATION] Periodic token refresh check failed: {}", e.getMessage());
        }
    }

    /** Silent token refresh without propagating errors */
    private void refreshTokenSilently(RedditApiKeyConfig key) {
        try {
            RedditApiKeyConfig fresh = rotationService.refreshTokenByAlias(key);
            if (fresh != null) {
                log.info("[ROTATION] Token for alias '{}' refreshed successfully", key.getAlias());
            }
        } catch (Exception e) {
            log.warn("[ROTATION] Token refresh failed for alias '{}': {}", key.getAlias(), e.getMessage());
        }
    }

    /**
     * Report active crawler count every 60 seconds for monitoring/logging.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void logActiveCrawlers() {
        try {
            long activeCount = jobRepo.findByStatus("RUNNING").size();
            long completedTotal = jobRepo.count();
            
            // P4-1: log rotation status alongside crawler health
            int validKeys = rotationService.getActiveKeyCount();

            log.info("[HEALTH] Crawler health: active={}, total jobs completed={}, valid API keys={}", 
                     activeCount, completedTotal, validKeys);
        } catch (Exception e) {
            log.error("[HEALTH] Status logging error", e);
        }
    }

    /* ──────────────────────────────────
     * @Async crawl execution
     * ────────────────────────────────── */

    /**
     * Submit a new crawl job asynchronously (non-blocking).
     * Returns the jobId immediately; actual crawling runs in the crawlExecutor pool.
     * P4-1: Now resolves active API key configs and stores in crawl params for RedditCrawlerService to pick up.
     */
    @Async("crawlExecutor")
    public void startCrawlAsync(String subreddit, Map<String, Object> config) {
        String jobId = UUID.randomUUID().toString();
        
        log.info("[ASYNC] Submitted async crawl {} for subreddit={} with {} params", 
                 jobId, subreddit, config != null ? config.size() : 0);

        // P4-1: Pick the next API key from round-robin rotation and attach to job config
        RedditApiKeyConfig activeKey = rotationService.peekCurrentApiKey();
        if (activeKey != null) {
            log.info("[ROTATION] Assigned key alias '{}' (rotation order {}) to crawl job {}", 
                     activeKey.getAlias(), activeKey.getRotationOrder(), jobId);
            
            // Merge key info into the config that gets passed to crawlerService.startCrawl()
            if (config == null) {
                config = new java.util.HashMap<>();
            }
            config.put("apiKeyAlias", activeKey.getAlias());
            config.put("apiKeyOrderId", activeKey.getRotationOrder());
            config.put("accessToken", activeKey.getAccessToken());  // pass directly to API client
            
        } else {
            log.warn("[ROTATION] No active API keys available for crawl job {}. Crawl may fail without credentials.", jobId);
        }

        try {
            // The synchronous crawl runs on this thread-pool worker.
            crawlerService.startCrawl(subreddit, config);
            
            log.info("[ASYNC] Crawl job {} completed (duration tracked via scheduled health-sweep).", jobId);
        } catch (Exception e) {
            log.error("[ASYNC] Async crawl {} failed for subreddit={}: {}", jobId, subreddit, e.getMessage(), e);
        }
    }

    /**
     * Returns the number of active RUNNING jobs visible to the scheduler.
     */
    public int getActiveCrawlerCount() {
        return jobRepo.findByStatus("RUNNING").size();
    }
}
