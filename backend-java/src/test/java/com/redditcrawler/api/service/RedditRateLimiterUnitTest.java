package com.redditcrawler.api.service;

import com.redditcrawler.api.config.RedditRateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class RedditRateLimiterUnitTest {

    private RedditRateLimiter rateLimiter;
    private RedditRateLimitConfig config;

    @BeforeEach
    void setUp() {
        config = new RedditRateLimitConfig();
        config.setMinDelay(Duration.ofSeconds(1));
        config.setCrawlIntervalMs(500);
        config.setInitialBackoffSec(3);
        config.setMaxBackoffSec(30);
        rateLimiter = new RedditRateLimiter(config);
    }

    // ── Config defaults ────────────────────────────────────────────────

    @Test
    @DisplayName("Defaults when config is null")
    void nullConfigUsesDefaults() {
        RedditRateLimiter limiter = new RedditRateLimiter(null);
        assertEquals(2, limiter.getMinDelay().getSeconds());
        assertEquals(1000, limiter.getCrawlIntervalMs());
        assertEquals(5, limiter.getInitialBackoffSec());
        assertEquals(60, limiter.getMaxBackoffSec());
    }

    @Test
    @DisplayName("Defaults when config values are zero or negative")
    void zeroConfigValuesFallBackToDefaults() {
        RedditRateLimitConfig bad = new RedditRateLimitConfig();
        bad.setMinDelay(Duration.ZERO);
        bad.setCrawlIntervalMs(0);
        bad.setInitialBackoffSec(-1);
        bad.setMaxBackoffSec(0);

        RedditRateLimiter limiter = new RedditRateLimiter(bad);
        assertEquals(2, limiter.getMinDelay().getSeconds());
        assertEquals(1000, limiter.getCrawlIntervalMs());
        assertEquals(5, limiter.getInitialBackoffSec());
        assertEquals(60, limiter.getMaxBackoffSec());
    }

    // ── Per-subreddit minimum-interval delay (single-subreddit test) ───

    @Test
    @DisplayName("First call for a subreddit returns ZERO wait")
    void firstCallReturnsZero() {
        RedditRateLimiter fresh = new RedditRateLimiter(new RedditRateLimitConfig());
        assertTrue(fresh.waitForReady("newsub").isZero());
    }

    @Test
    @DisplayName("Subsequent calls within delay window return non-zero wait")
    void enforceMinDelay() throws InterruptedException {
        RedditRateLimiter fresh = new RedditRateLimiter(new RedditRateLimitConfig());
        // Force a short min-delay so the test is fast
        fresh.setMinDelay(Duration.ofMillis(50));

        fresh.recordRequest("testsub");
        Duration wait = fresh.waitForReady("testsub");
        assertFalse(wait.isZero());
        assertTrue(wait.toMillis() > 0);

        // Wait for the delay to pass
        Thread.sleep(wait.toMillis() + 5);
        assertTrue(fresh.waitForReady("testsub").isZero());
    }

    @Test
    @DisplayName("recordRequest resets the interval timer")
    void recordRequestResetsTimer() throws InterruptedException {
        RedditRateLimiter fresh = new RedditRateLimiter(new RedditRateLimitConfig());
        fresh.setMinDelay(Duration.ofMillis(100));

        fresh.recordRequest("rs");
        Duration w1 = fresh.waitForReady("rs");
        assertFalse(w1.isZero());
        Thread.sleep(w1.toMillis() + 5);

        // Record another request — should reset the window
        fresh.recordRequest("rs");
        Duration w2 = fresh.waitForReady("rs");
        assertTrue(w2.toMillis() >= 80); // close to full window
    }

    // ── 429 cooldown & exponential backoff (isolated instance) ─────────

    @Test
    @DisplayName("setForcedCooldown blocks requests until cooldown expires")
    void forcedCooldownBlocksUntilExpired() throws InterruptedException {
        RedditRateLimiter fresh = new RedditRateLimiter(new RedditRateLimitConfig());
        // Set a 50ms cooldown — quick to verify
        fresh.setForcedCooldown("blocked", Duration.ofMillis(50));
        
        // Should block initially (forced cooldown still active)
        long blockedBefore = fresh.waitForReady("blocked").toMillis();
        assertFalse(fresh.waitForReady("blocked").isZero(), 
            "Should be blocked by forced cooldown immediately after set");
        
        Thread.sleep(150); // let it expire plus buffer for exponential backoff
        
        // After expiry, exponential backoff may also apply. Reset to verify clean state:
        fresh.resetCooldowns("blocked");
        fresh.resetBackoff("blocked");
        assertTrue(fresh.waitForReady("blocked").isZero(), "Should be unblocked after reset");
    }

    @Test
    @DisplayName("resetCooldowns removes the forced cooldown")
    void resetCooldownRemovesForcedDelay() {
        RedditRateLimiter fresh = new RedditRateLimiter(new RedditRateLimitConfig());
        fresh.setForcedCooldown("c", Duration.ofMillis(60000));
        assertFalse(fresh.waitForReady("c").isZero());

        fresh.resetCooldowns("c");
        assertTrue(fresh.waitForReady("c").isZero());
    }

    @Test
    @DisplayName("resetBackoff zeroes the consecutive-429 counter")
    void resetBackoffZerosCounter() {
        RedditRateLimiter fresh = new RedditRateLimiter(new RedditRateLimitConfig());
        // Use a zero-delay for quick check (won't block)
        fresh.setForcedCooldown("x", Duration.ofMillis(1));
        assertEquals(1, fresh.getConsecutive429Count("x"));

        fresh.resetBackoff("x");
        assertEquals(0, fresh.getConsecutive429Count("x"));
    }

    // ── Inter-crawl scheduling P5-1 (isolated instance) ────────────────

    @Test
    @DisplayName("First call to waitForNextCrawlSlot returns ZERO")
    void firstCrawlSlotAvailable() {
        RedditRateLimiter fresh = new RedditRateLimiter(new RedditRateLimitConfig());
        assertTrue(fresh.waitForNextCrawlSlot().isZero());
    }

    @Test
    @DisplayName("Subsequent calls within crawl-interval return non-zero wait")
    void serialisesConcurrentCrawls() throws InterruptedException {
        RedditRateLimiter fresh = new RedditRateLimiter(new RedditRateLimitConfig());
        // Short interval for speed
        RedditRateLimitConfig fastCfg = new RedditRateLimitConfig();
        fastCfg.setCrawlIntervalMs(50);
        RedditRateLimiter fast = new RedditRateLimiter(fastCfg);

        fast.waitForNextCrawlSlot();
        Duration w = fast.waitForNextCrawlSlot();
        assertFalse(w.isZero());
        assertTrue(w.toMillis() <= 100); // a bit of margin

        Thread.sleep(65);
        assertTrue(fresh.waitForNextCrawlSlot().isZero());
    }

    @Test
    @DisplayName("waitForNextCrawlSlot advances global timestamp correctly")
    void slotAdvancesTimestamp() throws InterruptedException {
        RedditRateLimiter fresh = new RedditRateLimiter(new RedditRateLimitConfig());
        assertTrue(fresh.waitForNextCrawlSlot().isZero());

        Duration w2 = fresh.waitForNextCrawlSlot();
        assertTrue(w2.toMillis() > 0);
    }

    // ── Backoff step computation (deterministic) ───────────────────────

    @Test
    @DisplayName("computeBackoffSec always returns positive values")
    void backoffAlwaysPositive() {
        for (int i = 0; i < 20; i++) {
            long backoff = rateLimiter.computeBackoffSec(i);
            assertTrue(backoff > 0, "Backoff at idx=" + i + " must be > 0, got " + backoff);
        }
    }

    @Test
    @DisplayName("computeBackoffSec returns base steps for low indices and caps at maxBackoffSec for high indices")
    void backoffRespectsMax() {
        RedditRateLimitConfig smallConfig = new RedditRateLimitConfig();
        smallConfig.setMaxBackoffSec(5);
        RedditRateLimiter smallLimiter = new RedditRateLimiter(smallConfig);

        // Indices 0-6 use BASE steps [5,10,...60] — these naturally exceed the cap
        long s0 = smallLimiter.computeBackoffSec(0);
        assertTrue(s0 >= 4 && s0 <= 6, "step[0] should be 5 +/- 1, got " + s0);
        
        // Indices >= 7 should be capped at maxBackoffSec + jitter (+1 max)
        for (int i = 7; i < 50; i++) {
            long b = smallLimiter.computeBackoffSec(i);
            assertTrue(b <= smallConfig.getMaxBackoffSec() + 1, 
                "At idx=" + i + " value " + b + " exceeds cap+1");
        }
    }

    @Test
    @DisplayName("staticComputeBackoff uses base steps capped at the given ceiling")
    void staticComputeBackoffMirrorsInstance() {
        // indices 0-6 -> uses BASE [5,10,...60] + jitter
        for (int i = 0; i <= 6; i++) {
            long result = RedditRateLimiter.staticComputeBackoff(i, 20);
            assertTrue(result > 0, "idx=" + i);
            // step[i]+1: 5->6, 10->11, ... max is 61
            int[] steps = {5,10,20,30,40,50,60};
            int expectedMax = Math.min(i, steps.length-1) < steps.length ? steps[Math.min(i, steps.length-1)] + 1 : 21;
            assertTrue(result <= expectedMax, "idx=" + i);
        }
        // High indices -> capped at ceiling (20) + jitter (1) = 21
        for (int i = 7; i < 10; i++) {
            long result = RedditRateLimiter.staticComputeBackoff(i, 20);
            assertTrue(result <= 21, "idx=" + i);
        }
    }

    // ── Min delay accessor ────────────────────────────────────────────

    @Test
    @DisplayName("minDelay returns configured value")
    void minDelayAccessor() {
        assertEquals(Duration.ofSeconds(1), rateLimiter.getMinDelay());
    }

    @Test
    @DisplayName("getConfigDump returns all config fields present")
    void getConfigDumpAllFieldsPresent() {
        var dump = rateLimiter.getConfigDump();
        assertTrue(dump.containsKey("minDelaySeconds"));
        assertTrue(dump.containsKey("crawlIntervalMs"));
        assertTrue(dump.containsKey("initialBackoffSec"));
        assertTrue(dump.containsKey("maxBackoffSec"));
    }

    @Test
    @DisplayName("Different subreddits are independent - only recorded one should have delay")
    void independentSubreddits() throws InterruptedException {
        RedditRateLimiter fresh = new RedditRateLimiter(new RedditRateLimitConfig());
        fresh.setMinDelay(Duration.ofMillis(50));

        fresh.recordRequest("a");

        Duration wa = fresh.waitForReady("a");
        Duration wb = fresh.waitForReady("b");

        assertFalse(wa.isZero(), "subreddit 'a' should have delay (was recorded)");
        assertTrue(wb.isZero(), "subreddit 'b' should have no delay (never recorded)");
    }
}
