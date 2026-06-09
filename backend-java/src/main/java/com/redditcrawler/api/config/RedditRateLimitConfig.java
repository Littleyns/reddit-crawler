package com.redditcrawler.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Binds {@code reddit.ratelimiter.*} properties from application config.
 * Provides sensible defaults so the crawler works without any extra config.
 */
@Configuration
@ConfigurationProperties(prefix = "reddit.ratelimiter")
public class RedditRateLimitConfig {

    /** Minimum delay between requests to the same subreddit (default 2 s). */
    private Duration minDelay = Duration.ofSeconds(2);

    /** Interval between start of different crawls (inter-crawl slot) in ms (default: 60s). */
    private long crawlIntervalMs = 60_000;

    /** Default initial backoff on 429, in seconds (default 5 s). */
    private int initialBackoffSec = 5;

    /** Maximum backoff window on continued 429s, in seconds (default 60 s). */
    private int maxBackoffSec = 60;

    /** Max retries before giving up when hitting rate limits (default 5). */
    @SuppressWarnings("FieldMayBeFinal") 
    private int maxRetriesOn429 = 5;

    public Duration getMinDelay() { return minDelay; }
    public void setMinDelay(Duration minDelay) { this.minDelay = minDelay; }

    public long getCrawlIntervalMs() { return crawlIntervalMs; }
    public void setCrawlIntervalMs(long crawlIntervalMs) { this.crawlIntervalMs = crawlIntervalMs; }

    public int getInitialBackoffSec() { return initialBackoffSec; }
    public void setInitialBackoffSec(int initialBackoffSec) { this.initialBackoffSec = initialBackoffSec; }

    public int getMaxBackoffSec() { return maxBackoffSec; }
    public void setMaxBackoffSec(int maxBackoffSec) { this.maxBackoffSec = maxBackoffSec; }

    public int getMaxRetriesOn429() { return maxRetriesOn429; }
    public void setMaxRetriesOn429(int maxRetriesOn429) { this.maxRetriesOn429 = maxRetriesOn429; }
}
