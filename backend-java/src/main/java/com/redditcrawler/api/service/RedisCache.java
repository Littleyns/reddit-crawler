package com.redditcrawler.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent Redis-backed queue for crawl jobs with configurable TTL expiry.
 * Used when Redis is available; falls back to in-memory CrawlJobStore if no connection detected.
 * 
 * Key structure:
 *   - `crawl:jobs`       (ZSet: jobId sorted by createdAt timestamp)
 *   - `crawl:pending`    (List: FIFO queue of pending jobIds)
 *   - `crawl:job:{id}`   (Hash: full job details per jobId, TTL = CRAWL_JOB_TTL_SECS)
 * 
 * <p>All keys are automatically garbage-collected by Redis after their expiry.</p>
 */
@Component
public class RedisCache {

    private static final Logger log = LoggerFactory.getLogger(RedisCache.class);
    private static final String JOB_LIST_KEY = "crawl:jobs";
    private static final String PENDING_QUEUE_KEY = "crawl:pending";
    private static final String JOB_DETAIL_PREFIX = "crawl:job:";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Default 30-minute TTL for crawl job keys. Overridable via REDIS_CRAWL_JOB_TTL env var. */
    public static final long DEFAULT_TTL_SECONDS = 1800;

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration keyTtl;
    private final boolean ttlEnabled;

    public RedisCache(
            RedisTemplate<String, String> redisTemplate,
            @Value("${redis.crawl.job-ttl:1800}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.keyTtl = Duration.ofSeconds(ttlSeconds);
        this.ttlEnabled = ttlSeconds > 0;

        if (ttlEnabled && ttlSeconds != DEFAULT_TTL_SECONDS) {
            log.info("Redis crawl job TTL configured: {} seconds", ttlSeconds);
        } else {
            log.info("Using default Redis crawl job TTL: {}s", DEFAULT_TTL_SECONDS);
        }
    }

    /** @return the configured TTL, or null if disabled */
    public Duration getKeyTtl() { return ttlEnabled ? keyTtl : null; }

    // ----- Enqueue / dequeue -----

    @SuppressWarnings("unchecked")
    public String enqueue(String subreddit, Map<String, Object> config) {
        String jobId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("jobId", jobId);
        fields.put("subreddit", subreddit);
        fields.put("status", "PENDING");
        fields.put("config", config != null ? config.toString() : "{}");
        fields.put("createdAt", Long.toString(now));

        String jobKey = JOB_DETAIL_PREFIX + jobId;

        redisTemplate.opsForHash().putAll(jobKey, fields);

        // Apply TTL to the job details hash
        applyTtl(jobKey);

        redisTemplate.opsForZSet().add(JOB_LIST_KEY, jobId, (double) now);
        log.info("Enqueued crawl job: {} for subreddit {} (TTL={}s)", jobId, subreddit, ttlEnabled ? keyTtl.getSeconds() : "none");
        return jobId;
    }

    public String dequeue() {
        Object result = redisTemplate.opsForList().leftPop(PENDING_QUEUE_KEY);
        return result == null ? null : (String) result;
    }

    // ----- Status updates -----

    public void updateStatus(String jobId, String status) {
        String key = JOB_DETAIL_PREFIX + jobId;
        redisTemplate.opsForHash().put(key, "status", status);
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            redisTemplate.opsForHash().put(key, "completedAt", java.time.Instant.now().toString());
            // Completed jobs keep their TTL until it expires naturally (don't hard-delete)
        } else if ("FAILED".equals(status)) {
            // Failed jobs also retain the key for debugging / review
            log.warn("Crawl job {} failed — retaining key for {}, will expire after {}", jobId, keyTtl, keyTtl.getSeconds() + "s");
        }
    }

    // ----- CRUD operations (all apply TTL automatically) -----

    public Map<Object, Object> getStatus(String jobId) {
        String key = JOB_DETAIL_PREFIX + jobId;
        if (!exists(key)) return java.util.Map.of();
        return redisTemplate.opsForHash().entries(key);
    }

    public void storeResults(String jobId, List<Map<String, Object>> results) throws RuntimeException {
        try {
            String json = JSON.writeValueAsString(results);
            String key = JOB_DETAIL_PREFIX + jobId;
            redisTemplate.opsForHash().put(key, "resultsJson", json);
            applyTtl(key); // refresh expiry on every store operation
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize results for Redis", e);
        }
    }

    public List<String> getAllJobIds() {
        Long size = redisTemplate.opsForZSet().zCard(JOB_LIST_KEY);
        if (size == null || size == 0) return java.util.List.of();
        Set<String> range = redisTemplate.opsForZSet().range(JOB_LIST_KEY, 0, size - 1);
        return range == null ? java.util.List.of() : new ArrayList<>(range);
    }

    public void updateResults(String jobId, List<Map<String, Object>> results) throws RuntimeException {
        storeResults(jobId, results);
        updateStatus(jobId, "COMPLETED");
    }

    public void updateComments(String jobId, List<Map<String, Object>> comments) throws RuntimeException {
        try {
            String json = JSON.writeValueAsString(comments);
            String key = JOB_DETAIL_PREFIX + jobId;
            redisTemplate.opsForHash().put(key, "commentsJson", json);
            applyTtl(key); // refresh expiry on every store operation
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize comments for Redis", e);
        }
    }

    public List<Map<String, Object>> loadComments(String jobId) throws RuntimeException {
        String key = JOB_DETAIL_PREFIX + jobId;
        if (!exists(key)) return java.util.List.of();
        Object val = redisTemplate.opsForHash().get(key, "commentsJson");
        if (val == null || val.toString().isEmpty()) return java.util.List.of();
        try {
            return JSON.readValue(val.toString(), JSON.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize comments from Redis", e);
        }
    }

    /** Delete job from all Redis structures immediately. TTL is not needed after explicit delete. */
    @SuppressWarnings("unchecked")
    public void deleteJob(String jobId) {
        String key = JOB_DETAIL_PREFIX + jobId;
        log.info("Deleting crawl job {} from Redis", jobId);
        redisTemplate.delete(key);
        redisTemplate.opsForZSet().remove(JOB_LIST_KEY, jobId);
        // Note: removed from pending queue is handled by the worker during dequeue,
        // but in case that didn't happen, try to remove it idempotently:
        try {
            redisTemplate.opsForList().remove(PENDING_QUEUE_KEY, 0L, jobId);
        } catch (Exception e) {
            log.debug("Job {} not in pending queue (already dequeued or expired)", jobId);
        }
    }

    /** Check if the crawl job key still exists (hasn't expired). */
    public boolean exists(String jobId) {
        String key = JOB_DETAIL_PREFIX + jobId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // ----- Helpers -----

    private void applyTtl(String key) {
        if (ttlEnabled && key != null) {
            Boolean set = redisTemplate.expire(key, keyTtl);
            if (Boolean.FALSE.equals(set)) {
                log.warn("Failed to set TTL on key {}", key);
            }
        }
    }

    /** Check if Redis is actually connected and usable. */
    public boolean isConnected() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return pong != null; // returns "PONG" on success
        } catch (Exception e) {
            log.warn("Redis not available, falling back to CrawlJobStore");
            return false;
        }
    }

    /** Get pending queue length. */
    public long getPendingCount() {
        Long len = redisTemplate.opsForList().size(PENDING_QUEUE_KEY);
        return len != null ? len : 0L;
    }
}
