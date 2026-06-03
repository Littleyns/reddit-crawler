package com.redditcrawler.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent Redis-backed queue for crawl jobs. Used when Redis is available; 
 * falls back to in-memory CrawlJobStore if no connection detected.
 * Key structure:
 *   - `crawl:jobs`       (ZSet: jobId sorted by createdAt timestamp)
 *   - `crawl:pending`    (List: FIFO queue of pending jobIds)
 *   - `crawl:job:{id}`   (Hash: full job details per jobId)
 */
@Component
public class RedisCache {

    private static final Logger log = LoggerFactory.getLogger(RedisCache.class);
    private static final String JOB_LIST_KEY = "crawl:jobs";
    private static final String PENDING_QUEUE_KEY = "crawl:pending";
    private static final String JOB_DETAIL_PREFIX = "crawl:job:";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RedisTemplate<String, String> redisTemplate;

    public RedisCache(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Enqueue a crawl job. Returns the jobId. */
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

        redisTemplate.opsForHash().putAll(JOB_DETAIL_PREFIX + jobId, fields);
        redisTemplate.opsForZSet().add(JOB_LIST_KEY, jobId, (double) now);
        redisTemplate.opsForList().rightPush(PENDING_QUEUE_KEY, jobId);

        log.info("Enqueued crawl job: {} for subreddit {}", jobId, subreddit);
        return jobId;
    }

    /** Dequeue the next pending job ID. Returns null if queue is empty. */
    public String dequeue() {
        Object result = redisTemplate.opsForList().leftPop(PENDING_QUEUE_KEY);
        return result == null ? null : (String) result;
    }

    /** Update job status in Redis (idempotent). */
    public void updateStatus(String jobId, String status) {
        String key = JOB_DETAIL_PREFIX + jobId;
        redisTemplate.opsForHash().put(key, "status", status);
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            redisTemplate.opsForHash().put(key, "completedAt", java.time.Instant.now().toString());
        }
    }

    /** Retrieve full job details from Redis. */
    public Map<Object, Object> getStatus(String jobId) {
        return redisTemplate.opsForHash().entries(JOB_DETAIL_PREFIX + jobId);
    }

    /** Store crawl results (JSON-serialized). */
    public void storeResults(String jobId, List<Map<String, Object>> results) throws RuntimeException {
        try {
            String json = JSON.writeValueAsString(results);
            redisTemplate.opsForHash().put(JOB_DETAIL_PREFIX + jobId, "resultsJson", json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize results for Redis", e);
        }
    }

    /** Get all jobIds sorted by createdAt. */
    public List<String> getAllJobIds() {
        Long size = redisTemplate.opsForZSet().zCard(JOB_LIST_KEY);
        if (size == null || size == 0) return java.util.List.of();
        Set<String> range = redisTemplate.opsForZSet().range(JOB_LIST_KEY, 0, size - 1);
        return range == null ? java.util.List.of() : new ArrayList<>(range);
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
        return len != null ? len : 0;
    }

    /** Update job results AND mark as COMPLETED. */
    public void updateResults(String jobId, List<Map<String, Object>> results) throws RuntimeException {
        storeResults(jobId, results);
        updateStatus(jobId, "COMPLETED");
    }

    /** Store crawl comments under a separate hash key per jobId. */
    public void updateComments(String jobId, List<Map<String, Object>> comments) throws RuntimeException {
        try {
            String json = JSON.writeValueAsString(comments);
            redisTemplate.opsForHash().put(JOB_DETAIL_PREFIX + jobId, "commentsJson", json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize comments for Redis", e);
        }
    }

    /** Get stored comments for a jobId (null if absent). */
    public List<Map<String, Object>> loadComments(String jobId) throws RuntimeException {
        Object val = redisTemplate.opsForHash().get(JOB_DETAIL_PREFIX + jobId, "commentsJson");
        if (val == null || val.toString().isEmpty()) return java.util.List.of();
        try {
            return JSON.readValue(val.toString(), JSON.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize comments from Redis", e);
        }
    }

    /** Delete a job from all structures. */
    @SuppressWarnings("unchecked")
    public void deleteJob(String jobId) {
        redisTemplate.delete(JOB_DETAIL_PREFIX + jobId);
        redisTemplate.opsForZSet().remove(JOB_LIST_KEY, jobId);
    }
}
