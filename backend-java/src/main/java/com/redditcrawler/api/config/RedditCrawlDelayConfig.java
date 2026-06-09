package com.redditcrawler.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Binds {@code reddit.crawl-delays.*} properties for per-subreddit crawl spacing.
 * 
 * Usage in application.yml / application-ratelimiter.yml:
 * <pre>
 *   reddit:
 *     crawl-delays:
 *       default-delay-millis: 60000        # default for all subreddits
 *       linux: 30000                         # per-subreddit override
 *       golang: 120000                       # per-subreddit override
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "reddit.crawl-delays")
public class RedditCrawlDelayConfig {

    /** Default delay between crawls (when no per-subreddit override exists). Default: 60s. */
    private long defaultDelayMillis = 60_000L;

    /** Per-subreddit override delays keyed by subreddit name (lowercase). */
    private Map<String, Long> delays = new HashMap<>();

    public long getDefaultDelayMillis() {
        return defaultDelayMillis;
    }

    public void setDefaultDelayMillis(long defaultDelayMillis) {
        this.defaultDelayMillis = defaultDelayMillis;
    }

    public Map<String, Long> getDelays() {
        return delays;
    }

    public void setDelays(Map<String, Long> delays) {
        this.delays = delays;
    }

    /**
     * Get the effective delay (in milliseconds) for a given subreddit.
     * Uses per-subreddit override if available, otherwise falls back to default.
     */
    public long getDelayForSubreddit(String subreddit) {
        String key = subreddit.toLowerCase().trim();
        Long override = delays.get(key);
        if (override != null && override > 0) {
            return override;
        }
        return getDefaultDelayMillis();
    }

    /** Convert default to Duration. */
    public Duration getDefaultDelay() {
        return Duration.ofMillis(defaultDelayMillis);
    }
}
