package com.redditcrawler.api.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service that crawls Reddit via the public Reddit API (oauth + JSON endpoint).
 * Uses Redis-backed queue when available; falls back to in-memory CrawlJobStore.
 */
@Service
public class RedditCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(RedditCrawlerService.class);

    private final RestTemplate restTemplate;
    private final CrawlJobStore jobStore;
    private final RedisCache redisCache;

    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);

    @Value("${reddit.oauth.client-id:}")
    private String clientId;

    @Value("${reddit.oauth.client-secret:}")
    private String clientSecret;

    @Value("${reddit.api.base-url:https://oauth.reddit.com}")
    private String redditApiBaseUrl;

    public RedditCrawlerService(RestTemplate restTemplate, CrawlJobStore jobStore, RedisCache redisCache) {
        this.restTemplate = restTemplate;
        this.jobStore = jobStore;
        this.redisCache = redisCache;
    }

    @PostConstruct
    void init() {
        boolean connected = redisCache.isConnected();
        redisAvailable.set(connected);
        if (connected) {
            log.info("[REDIS-QUEUE] Redis connection established — using distributed queue");
        } else {
            log.warn("[REDIS-QUEUE] Redis not available — falling back to in-memory CrawlJobStore");
        }
    }

    /** Return true if a distributed crawl worker should be launched (Redis available). */
    public boolean isDistributedMode() {
        return redisAvailable.get();
    }

    /** Get current queue length from Redis, or -1 if not connected. */
    public long getPendingQueueLength() {
        return redisAvailable.get() ? redisCache.getPendingCount() : -1;
    }

    /**
     * Start a crawl job for the given subreddit. Returns the jobId.
     * Uses Redis queue if available, otherwise in-memory store.
     */
    public String startCrawl(String subreddit, Map<String, Object> config) {
        if (redisAvailable.get()) {
            // Distribute via Redis — worker picks up later
            return redisCache.enqueue(subreddit, config);
        }

        // Fallback: in-memory crawl
        String jobId = UUID.randomUUID().toString();

        Map<String, Object> job = new LinkedHashMap<>();
        job.put("jobId", jobId);
        job.put("subreddit", subreddit);
        job.put("status", "RUNNING");
        job.put("config", config != null ? config : Map.of());
        job.put("resultsJson", List.<Map<String, Object>>of());
        job.put("startedAt", Instant.now().toString());
        job.put("completedAt", null);

        jobStore.put(jobId, job);

        // Perform the crawl synchronously on this thread (no worker pool in fallback mode)
        doCrawlSync(jobId, subreddit, config);

        return jobId;
    }

    /**
     * Internal crawl logic: fetches posts from Reddit JSON endpoint.
     * Runs synchronously for the in-memory backend.
     */
    @SuppressWarnings("unchecked")
    private void doCrawlSync(String jobId, String subreddit, Map<String, Object> config) {
        try {
            // Get OAuth token
            String accessToken = getAccessToken();

            // Construct the /r/{sub}/hot.json or similar URL
            int limit = 25;
            if (config != null && config.get("limit") instanceof Integer) {
                limit = (Integer) config.get("limit");
            }

            String url = redditApiBaseUrl + "/r/" + subreddit + "/hot.json?limit=" + Math.min(limit, 100);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Map.class);

            if (response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                List<Map<String, Object>> children =
                        (List<Map<String, Object>>) data.get("children");

                List<Map<String, Object>> crawledPosts = new ArrayList<>();
                if (children != null) {
                    for (Map<String, Object> child : children) {
                        Map<String, Object> postJson = new LinkedHashMap<>();
                        Map<String, Object> d = (Map<String, Object>) child.get("data");

                        String permalink = "https://www.reddit.com" + (String) d.get("permalink");
                        postJson.put("title", d.getOrDefault("title", ""));
                        postJson.put("body", cleanBody(d.getOrDefault("selftext", "").toString()));
                        postJson.put("author", d.getOrDefault("author", "[deleted]"));
                        postJson.put("upvotes", d.getOrDefault("ups", 0));
                        postJson.put("commentsCount", d.getOrDefault("num_comments", 0));
                        postJson.put("createdUtc", Math.toIntExact((long) (Double.parseDouble(d.getOrDefault("created_utc", "0").toString()))));
                        postJson.put("permalink", permalink);
                        postJson.put("subreddit", subreddit);

                        crawledPosts.add(postJson);
                    }
                }

                // Store results back to job
                if (redisAvailable.get()) {
                    redisCache.updateResults(jobId, crawledPosts);
                } else {
                    jobStore.updateResults(jobId, crawledPosts);
                    jobStore.updateStatus(jobId, "COMPLETED");
                }
            } else {
                String status = "FAILED_NO_DATA";
                if (redisAvailable.get()) {
                    redisCache.updateStatus(jobId, status);
                } else {
                    jobStore.updateStatus(jobId, status);
                }
            }
        } catch (Exception e) {
            log.error("Crawl failed for jobId=" + jobId, e);
            String errorStatus = "FAILED:" + e.getMessage();
            if (redisAvailable.get()) {
                redisCache.updateStatus(jobId, errorStatus);
            } else {
                jobStore.updateStatus(jobId, errorStatus);
            }
        }
    }

    /**
     * Obtain a Bearer access token from Reddit OAuth endpoint.
     */
    private String getAccessToken() throws Exception {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            log.warn("Reddit OAuth credentials not configured — crawl will likely return no data");
            return "";
        }

        String credentials = clientId + ":" + clientSecret;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.ISO_8859_1));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "client_credentials");
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "https://www.reddit.com/api/v1/access_token",
                HttpMethod.POST, entity, Map.class);

        if (resp.getBody() == null) {
            throw new RuntimeException("Reddit OAuth returned empty response");
        }
        return (String) resp.getBody().get("access_token");
    }

    /**
     * Get current status of a crawl job.
     */
    public Map<String, Object> getStatus(String jobId) {
        if (redisAvailable.get()) {
            Map<Object, Object> entries = redisCache.getStatus(jobId);
            if (entries == null || entries.isEmpty()) {
                // Job may have fallen through to in-memory store as fallback
                return jobStore.get(jobId);
            }
            // Convert Redis Map<Object, Object> to the expected format
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<Object, Object> e : entries.entrySet()) {
                result.put(e.getKey().toString(), e.getValue());
            }
            return result;
        }
        return jobStore.get(jobId);
    }

    /**
     * Stop (cancel) a crawl job.
     */
    public List<Map<String, Object>> getAllJobs() {
        if (redisAvailable.get()) {
            Map<String, Object> allMap = new LinkedHashMap<>();
            // Build list from Redis by reading all entries
            for (String jobId : redisCache.getAllJobIds()) {
                Map<Object, Object> entries = redisCache.getStatus(jobId);
                if (entries != null && !entries.isEmpty()) {
                    Map<String, Object> job = new LinkedHashMap<>();
                    for (Map.Entry<Object, Object> e : entries.entrySet()) {
                        job.put(e.getKey().toString(), e.getValue());
                    }
                    return List.of(job); // Return single for now; can iterate allJobIds further
                }
            }
            return List.of();
        }
        return new ArrayList<>(jobStore.getAll());
    }

    public void stopCrawl(String jobId) {
        if (redisAvailable.get()) {
            redisCache.updateStatus(jobId, "CANCELLED");
        } else if (jobStore.containsKey(jobId)) {
            jobStore.updateStatus(jobId, "CANCELLED");
        }
    }

    /** Check whether Redis connection is currently active. */
    public boolean checkRedisConnection() {
        log.info("Health probe: Redis.isConnected() -> {}", redisCache.isConnected());
        if (redisAvailable.compareAndSet(false, redisCache.isConnected())) {
            log.info("[REDIS-QUEUE] Redis reconnected after earlier failure");
        } else if (redisAvailable.compareAndSet(true, !redisCache.isConnected())) {
            log.warn("[REDIS-QUEUE] Redis disconnected! Falling back to CrawlJobStore.");
            // Drain pending items from Redis back into jobStore as a safety net
            // In production this should happen via a dedicated drain loop
        }
        return redisAvailable.get();
    }

    /**
     * Clean up post body (remove excessive whitespace).
     */
    private String cleanBody(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("\\s+", " ").trim();
    }

    public static class PostDTO {
        @SuppressWarnings("unchecked")
        public static List<PostDTO> from(Map<String, Object> postJson) {
            if (postJson == null) return java.util.List.of();
            PostDTO dto = new PostDTO();
            dto.title = (String) postJson.getOrDefault("title", "");
            dto.body = (String) postJson.getOrDefault("body", "");
            dto.author = (String) postJson.getOrDefault("author", "[deleted]");
            Object upvotesObj = postJson.get("upvotes");
            if (upvotesObj instanceof Number n) {
                dto.upvotes = n.intValue();
            } else {
                dto.upvotes = 0;
            }
            Object commentsObj = postJson.get("commentsCount");
            if (commentsObj instanceof Number n) {
                dto.commentsCount = n.intValue();
            } else {
                dto.commentsCount = 0;
            }
            dto.createdUtc = Instant.ofEpochSecond(
                    (long) Double.parseDouble(postJson.getOrDefault("createdUtc", "0").toString()));
            dto.permalink = (String) postJson.getOrDefault("permalink", "");
            dto.subreddit = (String) postJson.getOrDefault("subreddit", "");
            return java.util.List.of(dto);
        }

        @SuppressWarnings("unchecked")
        public static List<PostDTO> fromResults(List<Map<String, Object>> results) {
            if (results == null) return java.util.List.of();
            var list = new ArrayList<PostDTO>();
            for (Map<String, Object> r : results) {
                list.addAll(from(r));
            }
            return list;
        }

        public String title;
        public String body;
        public String author;
        public int upvotes;
        public int commentsCount;
        public Instant createdUtc;
        public String permalink;
        public String subreddit;
    }
}
