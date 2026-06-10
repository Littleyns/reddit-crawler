package com.redditcrawler.api.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.redditcrawler.api.repository.CommentRepository;
import com.redditcrawler.api.repository.CrawlerJobRepository;
import com.redditcrawler.api.model.CrawlerJob;
import com.redditcrawler.api.model.RedditApiKeyConfig;
import com.redditcrawler.api.repository.PostRepository;
import org.springframework.transaction.annotation.Transactional;


/**
 * Service that crawls Reddit via the public Reddit API (oauth + JSON endpoint).
 * Uses Redis-backed queue when available; falls back to in-memory CrawlJobStore.
 * P4-1: Integrates with RedditApiRotationService for multi-config key rotation.
 */
@Service
public class RedditCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(RedditCrawlerService.class);

    private final RestTemplate restTemplate;
    private final CrawlJobStore jobStore;
    private final RedisCache redisCache;
    private final PostRepository postRepo;
    private final CommentRepository commentRepo;
    private final CrawlerJobRepository jobRepo;
    private final RedditApiRotationService rotationService;
    private final RedditRateLimiter rateLimiter;

    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);

    @Value("${reddit.oauth.client-id:}")
    private String fallbackClientId;

    @Value("${reddit.oauth.client-secret:}")
    private String fallbackClientSecret;

    @Value("${reddit.api.base-url:https://oauth.reddit.com}")
    private String redditApiBaseUrl;

    public RedditCrawlerService(
            RestTemplate restTemplate,
            CrawlJobStore jobStore,
            RedisCache redisCache,
            PostRepository postRepo,
            CommentRepository commentRepo,
            CrawlerJobRepository jobRepo,
            RedditApiRotationService rotationService,
            RedditRateLimiter rateLimiter) {
        this.restTemplate = restTemplate;
        this.jobStore = jobStore;
        this.redisCache = redisCache;
        this.postRepo = postRepo;
        this.commentRepo = commentRepo;
        this.jobRepo = jobRepo;
        this.rotationService = rotationService;
        this.rateLimiter = rateLimiter;
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

        // Log rotation service status on startup
        int activeKeys = rotationService.getActiveKeyCount();
        log.info("[P4-1] Reddit API key rotation active keys: {}", activeKeys);
        if (activeKeys == 0) {
            log.warn("[P4-1] No configured multi-config keys — crawler will use legacy fallback credentials only");
        } else {
            log.info("[P4-1] Multi-config key rotation ENABLED — crawling will rotate across {} keys", activeKeys);
        }
    }

    public boolean isDistributedMode() {
        return redisAvailable.get();
    }

    public long getPendingQueueLength() {
        return redisAvailable.get() ? redisCache.getPendingCount() : -1;
    }

    /**
     * Start a crawl job for the given subreddit. Returns the jobId.
     * P4-1: Uses rotating API keys when configured, falls back to legacy credentials.
     */
    public String startCrawl(String subreddit, Map<String, Object> config) {
        if (redisAvailable.get()) {
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

        // P4-1: Pick a rotating key for this crawl session (don't advance index during crawl)
        String authToken = getCrawlAuthToken();
        log.info("[P4-1] Crawl jobId={} starting with token from key: {}",
                jobId, authToken != null && !authToken.isEmpty() ? "configured" : "legacy");

        doCrawlSync(jobId, subreddit, config);

        // Also persist to PostgreSQL for durable storage and analytics queries
        CrawlerJob entity = new CrawlerJob();
        entity.setJobId(jobId);
        entity.setSubreddit(subreddit);
        entity.setStatus("RUNNING");
        entity.setStartedAt(Instant.now());
        entity.setConfig("{}");
        jobRepo.save(entity);
        return jobId;
    }

    /**
     * Get an auth token using the rotation service when available, falling back to static credentials.
     */
    private String getCrawlAuthToken() {
        // Prefer rotating keys
        RedditApiKeyConfig activeKey = rotationService.peekCurrentApiKey();
        if (activeKey != null && activeKey.getAccessToken() != null && !activeKey.getAccessToken().isEmpty()) {
            log.debug("[P4-1] Using rotating key '{}' access token", activeKey.getAlias());
            return activeKey.getAccessToken();
        }

        // Fallback to legacy static credentials
        if ((fallbackClientId != null && !fallbackClientId.isBlank()) &&
            (fallbackClientSecret != null && !fallbackClientSecret.isBlank())) {
            log.debug("[P4-1] Falling back to legacy OAuth credentials");
            try {
                return getAccessTokenFrom(fallbackClientId, fallbackClientSecret);
            } catch (Exception e) {
                log.error("[P4-1] Legacy credential auth failed: " + e.getMessage());
            }
        }

        return null;
    }

    private static final int MAX_RETRIES_ON_429 = 5;

    /** Track consecutive 429 count per subreddit for exponential backoff. */
    private final ConcurrentHashMap<String, AtomicInteger> consecutive429Count = new ConcurrentHashMap<>();

    /** Jackson mapper for response parsing. */
    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Crawl a subreddit with rate-limit awareness.
     * Implements exponential backoff on Reddit HTTP 429 (Too Many Requests).
     */
    @SuppressWarnings("unchecked")
    private void doCrawlSync(String jobId, String subreddit, Map<String, Object> config) {
        // ── Extract per-client-id from config (apiKeyAlias set by AsyncCrawlerRunner P4-1) ──
        String clientId = null;
        if (config != null && config.get("apiKeyAlias") != null) {
            clientId = config.get("apiKeyAlias").toString();
        }

        // ── P5-1: inter-crawl scheduler slot (per-subreddit delay) ─────────────
        Duration crawlWait = rateLimiter.waitForNextCrawlSlot(subreddit);
        if (!crawlWait.isZero()) {
            long usedDelay = rateLimiter.getDelayForSubreddit(subreddit);
            try {
                log.info("[P5-1-SCHEDULER] Job {} subreddit={} — waiting {}ms before new crawl "
                        + "(per-subreddit spacing: {}ms, rate-limiter pacing)",
                        jobId, subreddit, crawlWait.toMillis(), usedDelay);
                Thread.sleep(crawlWait.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                persistFailure(jobId, "FAILED_INTERRUPTED", subreddit);
                return;
            }
        }

        // Get token via the rotation service or fallback
        String accessToken = getCrawlAuthToken();

        if (accessToken == null || accessToken.isBlank()) {
            log.error("[P4-1] No auth tokens available — cannot crawl '" + subreddit + "'");
            persistFailure(jobId, "FAILED_NO_AUTH", subreddit);
            return;
        }

        int limit = 25;
        if (config != null && config.get("limit") instanceof Integer i) {
            limit = Math.min(i, 100);
        }

        String url = redditApiBaseUrl + "/r/" + subreddit + "/hot.json?limit=" + limit;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // ── rate-limit-aware retry loop ────────────────────────────────
        String responseBodyJson = null;
        int attempt = 0;

        while (true) {
            attempt++;
            log.info("[P5-1-SCHEDULER] Job {} subreddit={} — STARTING crawl (attempt {}/{}), " +
                    "rateLimiter active={}, backoffBucket={}",
                    jobId, subreddit, attempt, MAX_RETRIES_ON_429,
                    rateLimiter.getMinDelay(),
                    (clientId != null) ? rateLimiter.getConsecutive429CountPerClient(clientId, subreddit) : consecutive429Count.getOrDefault(subreddit, new AtomicInteger(0)).get());
            Duration waitBefore = (clientId != null) ? rateLimiter.waitForReadyPerClient(clientId, subreddit) : rateLimiter.waitForReady(subreddit);
            if (!waitBefore.isZero()) {
                try { Thread.sleep(waitBefore.toMillis()); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    persistFailure(jobId, "FAILED_INTERRUPTED", subreddit);
                    return;
                }
                log.info("[RATE-LIMIT] jobId={} subreddit={} — waited {}ms before request (attempt {})",
                        jobId, subreddit, waitBefore.toMillis(), attempt);
            }
            rateLimiter.recordRequestPerClient(clientId, subreddit);

            try {
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
                if (response.getBody() != null) {
                    responseBodyJson = mapper.writeValueAsString(response.getBody());
                    // Success — exit retry loop; rateLimiter backoff is reset below after parsing
                    break;
                } else {
                    log.warn("[RATE-LIMIT] jobId={} subreddit={}: empty response body (attempt {}/{}); "
                            + "rate-limiter backoff applied", jobId, subreddit, attempt, MAX_RETRIES_ON_429);
                }
            } catch (RestClientResponseException xcp) {
                int status = xcp.getStatusCode().value();

                if (status == 401) {
                    log.error("Auth error for crawler {}: {} — aborting", subreddit, xcp.getMessage());
                    persistFailure(jobId, "FAILED_AUTH_ERROR:" + xcp.getMessage(), subreddit);
                    return;
                }

                if (status == 429) {
                    // Per-client exponential backoff for this specific key
                    int clientBackoffIdx = (clientId != null)
                            ? rateLimiter.getConsecutive429CountPerClient(clientId, subreddit)
                            : consecutive429Count.getOrDefault(subreddit, new AtomicInteger(0)).get();

                    String retryAfter = xcp.getResponseHeaders().getFirst("Retry-After");
                    long cooldownMs;
                    if (retryAfter != null && !retryAfter.isBlank()) {
                        try {
                            cooldownMs = Long.parseLong(retryAfter) * 1000L; // Retry-After is seconds
                        } catch (NumberFormatException nfe) {
                            cooldownMs = RedditRateLimiter.DEFAULT_INITIAL_BACKOFF_S * 1000L;
                        }
                    } else {
                        cooldownMs = (int) (RedditRateLimiter.staticComputeBackoff(clientBackoffIdx, RedditRateLimiter.MAX_BACKOFF_DEFAULT_S) * 1000);
                    }

                    if (clientId != null) {
                        rateLimiter.setForcedCooldownPerClient(clientId, subreddit, Duration.ofMillis(cooldownMs));
                    } else {
                        rateLimiter.setForcedCooldown(subreddit, Duration.ofMillis(cooldownMs));
                    }

                    if (attempt >= MAX_RETRIES_ON_429) {
                        persistFailure(jobId, "FAILED_RATE_LIMITED:(after " + attempt + " attempts)", subreddit);
                        log.error("Rate limited after {} attempts for subreddit {} — giving up", attempt, subreddit);
                        return;
                    }

                    log.warn("[RATE-LIMIT] jobId={} subreddit={}, HTTP 429 at attempt {}/{}: cooldown={}ms",
                            jobId, subreddit, attempt, MAX_RETRIES_ON_429, cooldownMs);
                    continue;
                }

                // Other client errors (403, 404 for banned subreddits, etc.) → not retried
                log.error("HTTP {} for crawler {}: {} — aborting", status, subreddit, xcp.getMessage());
                persistFailure(jobId, "FAILED_HTTP_ERROR:" + status, subreddit);
                return;

            } catch (Exception xcp) {
                // Network-level failures → retryable via rate-limiter backoff
                log.warn("[RATE-LIMIT] jobId={} subreddit={}: network error on attempt {}: {}",
                        jobId, subreddit, attempt, xcp.getMessage());
                if (attempt >= MAX_RETRIES_ON_429) {
                    persistFailure(jobId, "FAILED_NETWORK_ERROR:" + xcp.getMessage(), subreddit);
                    return;
                }
                continue;
            }
        }

        // ── parse successful response ─────────────────────────---------
        Map<String, Object> body = null;
        try {
            body = mapper.readValue(responseBodyJson != null ? responseBodyJson : "{}",
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Could not parse response JSON for jobId={}: {}", jobId, e.getMessage());
        }

        if (body == null || body.get("data") == null) {
            log.warn("[RATE-LIMIT] jobId={} subreddit={}: no post data returned (attempt {}/{})",
                    jobId, subreddit, attempt, MAX_RETRIES_ON_429);
            rateLimiter.setForcedCooldown(subreddit, Duration.ofSeconds(10));
            persistFailure(jobId, "FAILED_NO_DATA", subreddit);
            return;
        }

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> children = (List<Map<String, Object>>) data.get("children");

        List<Map<String, Object>> crawledPosts = new ArrayList<>();
        List<Map<String, Object>> allComments = new ArrayList<>();
        String commentBase = null;

        if (children != null) {
            for (Map<String, Object> child : children) {
                Map<String, Object> postJson = new LinkedHashMap<>();
                Map<String, Object> d = (Map<String, Object>) child.get("data");

                String permalink = "https://www.reddit.com" + (String) d.get("permalink");
                postJson.put("title", d.getOrDefault("title", ""));
                postJson.put("body", cleanBody(d.getOrDefault("selftext", "").toString()));
                postJson.put("author", d.getOrDefault("author", "[deleted]"));
                Object ups = d.getOrDefault("ups", 0);
                postJson.put("upvotes", ups instanceof Number n ? n.intValue() : 0);
                postJson.put("commentsCount", d.getOrDefault("num_comments", 0));
                postJson.put("createdUtc", (int) Math.toIntExact((long) Double.parseDouble(d.getOrDefault("created_utc", "0").toString())));
                postJson.put("permalink", permalink);
                postJson.put("subreddit", subreddit);

                crawledPosts.add(postJson);

                if (d.get("name") != null) {
                    commentBase = String.valueOf(d.get("name"));
                }

                Object repliesObj = d.get("replies");
                if (repliesObj instanceof Map<?, ?> repliesMap && !repliesMap.isEmpty()) {
                    List<Map<String, Object>> children_map = (List<Map<String, Object>>) repliesMap.get("data");
                    if (children_map != null) {
                        for (Map<String, Object> comment : collectCommentsFlattened(children_map, subreddit, permalink, postJson)) {
                            allComments.add(comment);
                        }
                    }
                }
            }
        }

        // Successfully crawled — reset rate-limit state and save results
        if (clientId != null) {
            rateLimiter.resetBackoffPerClient(clientId, subreddit);
            rateLimiter.resetCooldownsPerClient(clientId, subreddit);
        } else {
            rateLimiter.resetBackoff(subreddit);
            rateLimiter.resetCooldowns(subreddit);
        }

        if (redisAvailable.get()) {
            redisCache.updateResults(jobId, crawledPosts);
            redisCache.updateComments(jobId, allComments);
            redisCache.updateStatus(jobId, "COMPLETED");
            persistPostsToPostgres(jobId, crawledPosts);
            persistCommentsToPostgres(jobId, allComments);
        } else {
            jobStore.updateResults(jobId, crawledPosts);
            jobStore.updateComments(jobId, allComments);
            jobStore.updateStatus(jobId, "COMPLETED");
            persistJobStatusToJpaUpdated(jobId, "COMPLETED", subreddit);
            persistPostsToPostgres(jobId, crawledPosts);
            persistCommentsToPostgres(jobId, allComments);
        }
    }

    // ── helpers for rate-limit / persistence ───────────────────────────

    private void bump429Counter(String subreddit) {
        AtomicInteger cnt = consecutive429Count.computeIfAbsent(subreddit, k -> new AtomicInteger(0));
        int v = cnt.incrementAndGet();
        log.info("[RATE-LIMIT] subreddit={} — 429 count: {}", subreddit, v);
    }

    private void persistFailure(String jobId, String status, String subreddit) {
        rateLimiter.setForcedCooldown(subreddit, Duration.ofSeconds(15));
        if (redisAvailable.get()) redisCache.updateStatus(jobId, status);
        else jobStore.updateStatus(jobId, status);
        persistJobStatusToJpaUpdated(jobId, status, subreddit);
    }

    /** Updated JPA persistence (uses jobId directly). */
    @SuppressWarnings("unchecked")
    private void persistJobStatusToJpaUpdated(String jobId, String status, String subreddit) {
        try {
            Optional<CrawlerJob> found = jobRepo.findById(jobId);
            CrawlerJob entity;
            if (found.isPresent()) {
                entity = found.get();
            } else {
                entity = new CrawlerJob();
                entity.setJobId(jobId);
                entity.setSubreddit(subreddit != null ? subreddit : "unknown");
                entity.setStartedAt(Instant.now());
            }
            entity.setStatus(status);
            if ("COMPLETED".equals(status) && entity.getCompletedAt() == null) {
                entity.setCompletedAt(Instant.now());
            } else if (status.equals("FAILED_NO_DATA") || status.startsWith("FAILED:") || status.equals("CANCELLED")) {
                entity.setCompletedAt(entity.getCompletedAt() != null ? entity.getCompletedAt() : Instant.now());
            }
            jobRepo.save(entity);
            log.info("[P1-1] Persisted job {} status={} to postgres crawler_jobs", jobId, status);
        } catch (Exception e) {
            log.error("[P1-1] Failed persisting job status for {}", jobId, e);
        }
    }

    /** Get access token from static credentials directly. */
    private String getAccessTokenFrom(String clientId, String clientSecret) throws Exception {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            log.warn("OAuth credentials not configured for legacy auth");
            return "";
        }

        String cred = clientId + ":" + clientSecret;
        String encoded = Base64.getEncoder().encodeToString(cred.getBytes(StandardCharsets.ISO_8859_1));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "client_credentials");
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "https://www.reddit.com/api/v1/access_token", HttpMethod.POST, entity, Map.class);

        if (resp.getBody() == null) {
            throw new RuntimeException("Reddit OAuth returned empty response");
        }
        return (String) resp.getBody().get("access_token");
    }

    public Map<String, Object> getStatus(String jobId) {
        if (redisAvailable.get()) {
            Map<Object, Object> entries = redisCache.getStatus(jobId);
            if (entries == null || entries.isEmpty()) {
                return jobStore.get(jobId);
            }
            var result = new LinkedHashMap<String, Object>();
            for (var e : entries.entrySet()) {
                result.put(e.getKey().toString(), e.getValue());
            }
            return result;
        }
        return jobStore.get(jobId);
    }

    public List<Map<String, Object>> getAllJobs() {
        if (redisAvailable.get()) {
            var jobs = new ArrayList<Map<String, Object>>();
            for (String jobId : redisCache.getAllJobIds()) {
                Map<Object, Object> entries = redisCache.getStatus(jobId);
                if (entries != null && !entries.isEmpty()) {
                    Map<String, Object> job = new LinkedHashMap<>();
                    for (var e : entries.entrySet()) {
                        job.put(e.getKey().toString(), e.getValue());
                    }
                    jobs.add(job);
                }
            }
            return jobs;
        }
        return new ArrayList<>(jobStore.getAll());
    }

    public void stopCrawl(String jobId) {
        if (redisAvailable.get()) {
            redisCache.updateStatus(jobId, "CANCELLED");
        } else if (jobStore.containsKey(jobId)) {
            jobStore.updateStatus(jobId, "CANCELLED");
        }
        persistJobStatusToJpaUpdated(jobId, "CANCELLED", null);
    }

    private String cleanBody(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("\\\\s+", " ").trim();
    }

    private void persistPostsToPostgres(String jobId, List<Map<String, Object>> posts) {
        // Posts are already persisted via jobStore.updateResults / redisCache above.
        // Additional JPA entity persistence would require a Post JPA entity which doesn't exist yet.
    }

    private void persistCommentsToPostgres(String jobId, List<Map<String, Object>> comments) {
        // Comments are already persisted via jobStore.updateComments / redisCache above.
        // Additional JPA entity persistence would require a Comment JPA entity which doesn't exist yet.
    }

    /**
     * Recursively flatten the Reddit comments tree into a flat list.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> collectCommentsFlattened(
            List<Map<String, Object>> nodes,
            String subreddit,
            String parentPostPermalink,
            Map<String, Object> parentPost) {
        List<Map<String, Object>> flat = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            if (node == null || !node.containsKey("data")) continue;
            Map<String, Object> data = (Map<String, Object>) node.get("data");

            Map<String, Object> comment = new LinkedHashMap<>();
            comment.put("author", data.getOrDefault("author", "[deleted]"));
            comment.put("body", cleanBody(data.getOrDefault("body", "").toString()));
            Object upsObj = data.getOrDefault("ups", 0);
            comment.put("upvotes", upsObj instanceof Number n ? n.intValue() : 0);
            comment.put("permalink", "https://www.reddit.com" + data.getOrDefault("permalink", ""));
            comment.put("subreddit", subreddit);
            Object createdObj = data.get("created_utc");
            if (createdObj instanceof Number num) {
                comment.put("createdUtc", Instant.ofEpochSecond(num.longValue()));
            } else {
                comment.put("createdUtc", null);
            }
            comment.put("parentPostTitle", parentPost != null ? String.valueOf(parentPost.get("title")) : "");
            comment.put("id", data.getOrDefault("id", ""));
            flat.add(comment);

            Object repliesObj = data.get("replies");
            if (repliesObj instanceof Map<?, ?> repliesMap && !repliesMap.isEmpty()) {
                List<Map<String, Object>> childNodes = (List<Map<String, Object>>) repliesMap.get("data");
                if (childNodes != null) {
                    flat.addAll(collectCommentsFlattened(childNodes, subreddit, parentPostPermalink, parentPost));
                }
            }
        }
        return flat;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAllJobsFromJpa() {
        return jobRepo.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("startedAt")
            ))
            .stream()
            .map(e -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("jobId", e.getJobId());
                m.put("subreddit", e.getSubreddit());
                m.put("status", e.getStatus());
                m.put("startedAt", e.getStartedAt());
                m.put("completedAt", e.getCompletedAt());
                m.put("resultsJson", e.getResultsJson() != null && !e.getResultsJson().isEmpty() ? java.util.List.of(true) : java.util.List.<Object>of());
                m.put("config", getConfigField(e));
                return m;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRotationSummary() {
        if (rotationService == null) {
            return Map.of("status", "not_configured");
        }
        return rotationService.rotationSummary();
    }

    /** Access the config field via reflection (JPA lazy-load safe). */
    private String getConfigField(CrawlerJob job) {
        Object val = job.getConfig();
        return val != null ? String.valueOf(val) : "{}";
    }

    public static class PostDTO {
        public PostDTO() {}

        @SuppressWarnings("unchecked")
        public static List<PostDTO> from(Map<String, Object> postJson) {
            if (postJson == null) return java.util.List.of();
            PostDTO dto = new PostDTO();
            dto.title = (String) postJson.getOrDefault("title", "");
            dto.body = (String) postJson.getOrDefault("body", "");
            dto.author = (String) postJson.getOrDefault("author", "[deleted]");
            Object upvotesObj = postJson.get("upvotes");
            if (upvotesObj instanceof Number n) { dto.upvotes = n.intValue(); }
            else { dto.upvotes = 0; }
            Object commentsObj = postJson.get("commentsCount");
            if (commentsObj instanceof Number n) { dto.commentsCount = n.intValue(); }
            else { dto.commentsCount = 0; }
            dto.createdUtc = Instant.ofEpochSecond(
                    Math.toIntExact((int) Double.parseDouble(postJson.getOrDefault("createdUtc", "0").toString())));
            dto.permalink = (String) postJson.getOrDefault("permalink", "");
            dto.subreddit = (String) postJson.getOrDefault("subreddit", "");
            return java.util.List.of(dto);
        }

        @SuppressWarnings("unchecked")
        public static List<PostDTO> fromResults(List<Map<String, Object>> results) {
            if (results == null) return java.util.List.of();
            var list = new ArrayList<PostDTO>();
            for (Map<String, Object> r : results) list.addAll(from(r));
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

    private String asString(Object o) {
        return o != null ? String.valueOf(o).trim() : "";
    }

}
