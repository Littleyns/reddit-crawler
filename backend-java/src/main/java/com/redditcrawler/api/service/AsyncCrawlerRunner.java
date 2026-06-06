package com.redditcrawler.api.service;

import com.redditcrawler.api.repository.CrawlerJobRepository;
import com.redditcrawler.api.model.CrawlerJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * P1-2: Async crawler runner with @Scheduled periodic health/status checks.
 * 
 * This service performs two roles:
 * 1) Schedules crawl jobs asynchronously via @Async on the crawlExecutor pool.
 * 2) Runs a scheduled health-sweep every 30 seconds to detect and mark stale RUNNING jobs.
 */
@Service
public class AsyncCrawlerRunner {

    private static final Logger log = LoggerFactory.getLogger(AsyncCrawlerRunner.class);

    private final RedditCrawlerService crawlerService;
    private final CrawlerJobRepository jobRepo;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public AsyncCrawlerRunner(RedditCrawlerService crawlerService, CrawlerJobRepository jobRepo) {
        this.crawlerService = crawlerService;
        this.jobRepo = jobRepo;
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
            log.info("[SCHEDULER] AsyncCrawlerRunner starting health-sweep");
            initialized.set(true);
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
                            if (j.getStartedAt() != null) {
                                long elapsedSecs = java.time.Duration.between(j.getStartedAt(), Instant.now()).getSeconds();
                                log.info("[SCHEDULER] Stale job {} ran for {} seconds before killing.", jobId, elapsedSecs);
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
    }

    /**
     * Report active crawler count every 60 seconds for monitoring/logging.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void logActiveCrawlers() {
        try {
            long activeCount = jobRepo.findByStatus("RUNNING").size();
            long completedTotal = jobRepo.count();
            
            log.info("[HEALTH] Crawler health: active={}, total jobs completed={}.", 
                     activeCount, completedTotal);
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
     */
    @Async("crawlExecutor")
    public void startCrawlAsync(String subreddit, Map<String, Object> config) {
        String jobId = UUID.randomUUID().toString();
        
        log.info("[ASYNC] Submitted async crawl {} for subreddit={} with {} params", 
                 jobId, subreddit, config != null ? config.size() : 0);
        
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
