package com.redditcrawler.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.redditcrawler.api.config.RedditCrawlDelayConfig;
import com.redditcrawler.api.config.RedditRateLimitConfig;

/**
 * Reddit API rate-limit awareness layer + P5-1 inter-crawl scheduler.
 *
 * Responsibilities:
 * 1. Enforce a configurable minimum delay between successive requests (default 2 s).
 * 2. Track last-request timestamps per subreddit so concurrent crawls for the same
 *    subreddit don't thunder.
 * 3. On receiving an HTTP 429 from Reddit, respect the Retry-After header (if present)
 *    and apply exponential backoff on subsequent attempts for that subreddit.
 * 4. Thread-safe — used by crawl workers across thread-pool threads.
 * P5-1: Global inter-crawl scheduler that spaces startup of parallel crawls across
 *       subreddits, preventing thundering-herd 429s when multiple crawls are
 *       submitted concurrently via @Async. Configurable via {@code reddit.ratelimiter.*}.
 */
@Service
public class RedditRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedditRateLimiter.class);

    // Default fallback constants (used only if no Spring config is supplied)
    public static final Duration DEFAULT_MIN_DELAY = Duration.ofSeconds(2);
    public static final int DEFAULT_INITIAL_BACKOFF_S = 5;
    public static final int MAX_BACKOFF_DEFAULT_S = 60;
    public static final long DEFAULT_CRAWL_INTERVAL_MS = 1_000L;

    /** Exponential back-off steps (before capping). */
    public static final int[] BACKOFF_STEPS = {5, 10, 20, 30, 40, 50, 60};

    // ── per-subreddit delay state ───────────────────────────────────────
    private final ConcurrentHashMap<String, Long> lastRequestAtMs = new ConcurrentHashMap<>();

    /** Per-subreddit retry-after override (ms). Set to >0 when a 429 is received. */
    private final ConcurrentHashMap<String, Long> forcedDelayUntilMs = new ConcurrentHashMap<>();

    /** Per-subreddit consecutive 429 count — drives exponential backoff. */
    private final ConcurrentHashMap<String, AtomicInteger> consecutive429Count = new ConcurrentHashMap<>();

    // ── global inter-crawl scheduling (P5-1) ───────────────────────────
    private volatile long nextCrawlReadyAtMs = 0;

    // ── injected config (immutable after construction) ────────────────
    private final Duration minDelay;
    /** Per-subreddit delay overrides (default 60s, configurable). */
    private final RedditCrawlDelayConfig crawlDelayConfig;
    private final long crawlIntervalMs;
    private final int initialBackoffSec;
    private final int maxBackoffSec;

    /** Configure via {@code reddit.ratelimiter.*} and {@code reddit.crawl-delays.*} Spring properties. */
    public RedditRateLimiter(RedditRateLimitConfig config, RedditCrawlDelayConfig crawlDelayConfig) {
        Duration cfgMinDelay = (config != null) ? config.getMinDelay() : null;
        this.minDelay = (cfgMinDelay != null && !cfgMinDelay.isZero())
                ? cfgMinDelay : DEFAULT_MIN_DELAY;

        // Combine rate-limiter and crawl-delay configs into final crawlIntervalMs
        if (crawlDelayConfig != null && crawlDelayConfig.getDefaultDelayMillis() > 0) {
            this.crawlIntervalMs = crawlDelayConfig.getDefaultDelayMillis();
        } else if (config != null && config.getCrawlIntervalMs() > 0) {
            this.crawlIntervalMs = config.getCrawlIntervalMs();
        } else {
            this.crawlIntervalMs = DEFAULT_CRAWL_INTERVAL_MS;
        }

        this.initialBackoffSec = (config != null && config.getInitialBackoffSec() > 0)
                ? config.getInitialBackoffSec() : DEFAULT_INITIAL_BACKOFF_S;
        this.maxBackoffSec = (config != null && config.getMaxBackoffSec() > 0)
                ? config.getMaxBackoffSec() : MAX_BACKOFF_DEFAULT_S;
        this.crawlDelayConfig = crawlDelayConfig != null ? crawlDelayConfig : new RedditCrawlDelayConfig();

        log.info("[RATE-LIMIT-CONFIG] Applied config: minDelay={}ms, crawlInterval={}ms, "
                + "initialBackoff={}s, maxBackoff={}s",
                minDelay.toMillis(), crawlIntervalMs, initialBackoffSec, maxBackoffSec);
        if (crawlDelayConfig != null) {
            log.info("[P5-1-CRAWL-DISPLAY] Default crawl delay: {}ms, per-subreddit overrides: {}",
                    crawlDelayConfig.getDefaultDelayMillis(),
                    crawlDelayConfig.getDelays().isEmpty() ? "[]" : crawlDelayConfig.getDelays().keySet());
        } else {
            log.warn("[P5-1-CRAWL-DISPLAY] No RedditCrawlDelayConfig bean found — using default 60s");
        }
    }

    // ── configuration overrides (runtime mutable fields) ───────────────
    private Duration runtimeMinDelay = null;

    /** Return effective min delay (runtime override takes priority). */
    private Duration effectiveMinDelay() {
        return (runtimeMinDelay != null) ? runtimeMinDelay : minDelay;
    }

    /** Look up the per-subreddit crawl delay in ms, falling back to default. */
    public long getDelayForSubreddit(String subreddit) {
        if (crawlDelayConfig != null && !crawlDelayConfig.getDelays().isEmpty()) {
            return crawlDelayConfig.getDelayForSubreddit(subreddit);
        }
        return crawlIntervalMs;
    }

    // ── rate-limit operations ──────────────────────────────────────────

    /** Override the forced cooldown for this tick. Used when the 429 Retry-After header
     *  demands a specific window rather than a backoff increment. */
    public synchronized void setForcedCooldown(String subreddit, Duration delay) {
        long until = System.currentTimeMillis() + delay.toMillis();
        forcedDelayUntilMs.put(subreddit, until);
        bumpBackoff(subreddit);
        log.info("[RATE-LIMIT] subreddit={} — setting cooldown for {}ms (forced by Retry-After)",
                subreddit, delay.toMillis());
    }

    /** Advance current exponential-backoff count. */
    private void bumpBackoff(String subreddit) {
        AtomicInteger counter = consecutive429Count.computeIfAbsent(subreddit, k -> new AtomicInteger(0));
        int bucket = counter.getAndUpdate(i -> Math.min(i + 1, 8));
        if (bucket < 8) {
            log.info("[RATE-LIMIT] subreddit={} — 429 count bumped to {}", subreddit, bucket + 1);
        }
    }

    /** Reset the consecutive-429 counter back to zero on success. */
    public void resetBackoff(String subreddit) {
        AtomicInteger counter = consecutive429Count.get(subreddit);
        if (counter != null && counter.get() > 0) {
            log.info("[RATE-LIMIT] subreddit={} — resetting 429 counter (success received)", subreddit);
            counter.set(0);
        }
    }

    // ── main entry point (called before every Reddit HTTP request) ──────

    /**
     * Determine whether the caller must wait before issuing a request for the given subreddit,
     * and how long that wait should be.
     *
     * @return Duration to sleep, or {@code Duration.ZERO} if no wait is needed.
     */
    public Duration waitForReady(String subreddit) {
        long now = System.currentTimeMillis();

        // ── forced cooldown (from a prior 429 Retry-After)? ────────────
        Long forcedEnd = forcedDelayUntilMs.get(subreddit);
        if (forcedEnd != null && now < forcedEnd) {
            long remaining = forcedEnd - now;
            log.info("[RATE-LIMIT] subreddit={} — sleeping {}ms (cooldown from prior 429)",
                    subreddit, remaining);
            return Duration.ofMillis(remaining);
        }

        // ── exponential backoff bucket ────────────────────────────────
        Long bucketEnd = forcedDelayUntilMs.computeIfPresent(subreddit, (k, v) -> {
            if (now < v) return v;  // still in cooldown from prior set
            int idx = consecutive429Count.getOrDefault(subreddit, new AtomicInteger(0)).get();
            long backoffSec = computeBackoffSec(idx);
            return now + backoffSec * 1000L;
        });

        if (bucketEnd != null && now < bucketEnd) {
            long remaining = bucketEnd - now;
            log.info("[RATE-LIMIT] subreddit={} — sleeping {}ms (exponential backoff)",
                    subreddit, remaining);
            return Duration.ofMillis(remaining);
        }

        // ── per-subreddit minimum-interval ──────────────────────────────
        Long last = lastRequestAtMs.get(subreddit);
        if (last != null) {
            long elapsed = now - last;
            long wait = effectiveMinDelay().toMillis() - elapsed;
            if (wait > 0) {
                log.info("[RATE-LIMIT] subreddit={} — sleeping {}ms (inter-request minimum)",
                        subreddit, wait);
                return Duration.ofMillis(wait);
            }
        }

        return Duration.ZERO;
    }

    /** Record that a request has been issued. */
    public void recordRequest(String subreddit) {
        lastRequestAtMs.put(subreddit, System.currentTimeMillis());
    }

    /** Reset all forced cooldowns for the given subreddit (called after successful response). */
    public void resetCooldowns(String subreddit) {
        forcedDelayUntilMs.remove(subreddit);
    }

    // ── configuration accessors ────────────────────────────────────────
    public Duration getMinDelay() { return effectiveMinDelay(); }
    long getCrawlIntervalMs() { return crawlIntervalMs; }
    int getInitialBackoffSec() { return initialBackoffSec; }
    int getMaxBackoffSec() { return maxBackoffSec; }

    /** Read current 429 count for a subreddit (for monitoring / reporting). */
    public int getConsecutive429Count(String subreddit) {
        AtomicInteger c = consecutive429Count.get(subreddit);
        return c != null ? c.get() : 0;
    }

    /** Dump current config as a Map for REST exposure. */
    public synchronized Map<String, Object> getConfigDump() {
        return Map.of(
            "minDelaySeconds", effectiveMinDelay().getSeconds(),
            "crawlIntervalMs", crawlIntervalMs,
            "initialBackoffSec", initialBackoffSec,
            "maxBackoffSec", maxBackoffSec
        );
    }

    /** Runtime mutator for min delay (used by management controller). */
    public synchronized void setMinDelay(Duration d) {
        this.runtimeMinDelay = d;
        log.info("[RATE-LIMIT] Runtime minDelay updated to {}ms", d.toMillis());
    }

    // ── backoff helpers ────────────────────────────────────────────────

    /**
     * Compute the backoff window in seconds for a given consecutive-429 bucket index.
     * Exponential step: 5, 10, 20, 30, 40, 50, 60, maxBackoffSec ... with jitter +/-1 s.
     */
    long computeBackoffSec(int idx) {
        int cap = 8;
        int size = Math.max(Math.min(cap, 2 + 8), 3);
        int[] steps = new int[size];
        for (int i = 0; i < Math.min(BACKOFF_STEPS.length, steps.length); i++) {
            steps[i] = BACKOFF_STEPS[i];
        }
        for (int i = BACKOFF_STEPS.length; i < steps.length; i++) {
            steps[i] = maxBackoffSec;
        }
        int val = Math.min(idx, steps.length - 1);
        long jitter = (long) (Math.random() * 2) - 1;
        return Math.max(1, steps[val] + jitter);
    }

    // ── static helper for use by RedditCrawlerService ──────────────────

    /**
     * Static back-off computation usable without an instance.
     * Useful during initial crawl attempts before the limiter is injected.
     */
    public static long staticComputeBackoff(int idx, int maxBackoffSec) {
        int size = Math.max(Math.min(8, 10), 3);
        int[] steps = new int[size];
        for (int i = 0; i < Math.min(BACKOFF_STEPS.length, steps.length); i++) {
            steps[i] = BACKOFF_STEPS[i];
        }
        for (int i = BACKOFF_STEPS.length; i < steps.length; i++) {
            steps[i] = maxBackoffSec;
        }
        int val = Math.min(idx, steps.length - 1);
        long jitter = (long) (Math.random() * 2) - 1;
        return Math.max(1, steps[val] + jitter);
    }

    // ── P5-1: inter-crawl scheduling ───────────────────────────────────


    /**
     * Called by a crawl worker BEFORE issuing any request for its subreddit.
     * Serialises the START of new crawls across all thread-pool workers so that
     * concurrent crawls for different subreddits never hammer Reddit simultaneously.
     *
     * Per-subreddit delays are applied when configured via {@code reddit.crawl-delays}.
     * Falls back to the config's default crawl-interval-ms (default: 60s).
     *
     * @param subreddit the subreddit being crawled (used for per-subreddit delay lookup)
     * @return Duration to sleep before starting, or {@code ZERO} if a slot is available now.
     */
    public Duration waitForNextCrawlSlot(String subreddit) {
        long delayMs = crawlDelayConfig.getDelayForSubreddit(subreddit);
        long now = System.currentTimeMillis();

        log.info("[P5-1-SCHEDULER] Crawl spacing: using per-subreddit delay={}ms for '{}'",
                delayMs, subreddit);

        synchronized (this) {
            if (now < nextCrawlReadyAtMs) {
                long remaining = nextCrawlReadyAtMs - now;
                // Respect the configured crawl spacing for this subreddit
                long sleepTime = Math.min(remaining, delayMs);
                log.info("[P5-1-SCHEDULER] Waiting {}ms before starting crawl for '{}' "
                        + "(next subreddit crawl at {})",
                        sleepTime, subreddit,
                        java.time.Instant.now().plusMillis(delayMs).toString());
                return Duration.ofMillis(sleepTime);
            }
            // Claim the next slot — advance global window by the per-subreddit delay
            nextCrawlReadyAtMs = now + delayMs;
        }

        log.info("[P5-1-SCHEDULER] Crawl slot claimed for subreddit='{}' "
                + "(spacing={}ms, next available at {})",
                subreddit, delayMs,
                java.time.Instant.now().plusMillis(delayMs).toString());
        return Duration.ZERO;
    }
}
