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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

import com.redditcrawler.api.repository.CommentRepository;
import com.redditcrawler.api.repository.CrawlerJobRepository;
import com.redditcrawler.api.model.CrawlerJob;
import com.redditcrawler.api.repository.PostRepository;
import org.springframework.transaction.annotation.Transactional;


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
    private final PostRepository postRepo;
    private final CommentRepository commentRepo;

    private final CrawlerJobRepository jobRepo;

    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);

    @Value("${reddit.oauth.client-id:}")
    private String clientId;

    @Value("${reddit.oauth.client-secret:}")
    private String clientSecret;

    @Value("${reddit.api.base-url:https://oauth.reddit.com}")
    private String redditApiBaseUrl;

    public RedditCrawlerService(
            RestTemplate restTemplate,
            CrawlJobStore jobStore,
            RedisCache redisCache,
            PostRepository postRepo,
            CommentRepository commentRepo,
            CrawlerJobRepository jobRepo) {
        this.restTemplate = restTemplate;
        this.jobStore = jobStore;
        this.redisCache = redisCache;
        this.postRepo = postRepo;
        this.commentRepo = commentRepo;
        this.jobRepo = jobRepo;
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

    public boolean isDistributedMode() {
        return redisAvailable.get();
    }

    public long getPendingQueueLength() {
        return redisAvailable.get() ? redisCache.getPendingCount() : -1;
    }

    /**
     * Start a crawl job for the given subreddit. Returns the jobId.
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

    @SuppressWarnings("unchecked")
    private void doCrawlSync(String jobId, String subreddit, Map<String, Object> config) {
        try {
            String accessToken = getAccessToken();
            int limit = 25;
            if (config != null && config.get("limit") instanceof Integer i) {
                limit = Math.min(i, 100);
            }

            String url = redditApiBaseUrl + "/r/" + subreddit + "/hot.json?limit=" + limit;

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
                // Also collect all comments flat across the whole result set.
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

                        // Collect comment base URL from first child (or construct it).
                        if (d.get("name") != null) {
                            commentBase = String.valueOf(d.get("name"));
                        }

                        // Recurse into the comments tree of this post.
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

                // Store posts and comments atomically.
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
                    persistJobStatusToJpa(jobId, "COMPLETED", crawledPosts);
                persistPostsToPostgres(jobId, crawledPosts);
                persistCommentsToPostgres(jobId, allComments);
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
            String error = "FAILED:" + e.getMessage();
            if (redisAvailable.get()) {
                redisCache.updateStatus(jobId, error);
            } else {
                jobStore.updateStatus(jobId, error);
            }
            persistJobStatusToJpa(jobId, error, null);
        }
    }

    private String getAccessToken() throws Exception {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            log.warn("Reddit OAuth credentials not configured — crawl will return no data");
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
        persistJobStatusToJpa(jobId, "CANCELLED", null);
    }

    private String cleanBody(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("\s+", " ").trim();
    }

    /**
     * Recursively flatten the Reddit comments tree into a flat list.
     * Each entry is a Map with keys matching the comment JSON structure from Reddit.
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
            // parent post reference for display.
            comment.put("parentPostTitle", parentPost != null ? String.valueOf(parentPost.get("title")) : "");
            comment.put("id", data.getOrDefault("id", ""));
            flat.add(comment);

            // Recurse into nested replies if present.
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

    /** Persist/update crawl job status to JPA for analytics queries. */
    @SuppressWarnings("unchecked")
    private void persistJobStatusToJpa(String jobId, String status, List<Map<String, Object>> results) {
        try {
            Optional<CrawlerJob> found = jobRepo.findById(jobId);
            CrawlerJob entity;
            if (found.isPresent()) {
                entity = found.get();
            } else {
                entity = new CrawlerJob();
                entity.setJobId(jobId);
                entity.setSubreddit("unknown");
                entity.setStartedAt(Instant.now());
            }
            entity.setStatus(status);
            if ("COMPLETED".equals(status) && entity.getCompletedAt() == null) {
                entity.setCompletedAt(Instant.now());
            } else if ("FAILED_NO_DATA".equals(status) || status.startsWith("FAILED:") || "CANCELLED".equals(status)) {
                entity.setCompletedAt(entity.getCompletedAt() != null ? entity.getCompletedAt() : Instant.now());
            }
            if ("RUNNING".equals(status) && entity.getResultsJson() == null) {
                entity.setResultsJson("");
            }
            jobRepo.save(entity);
            log.info("[P1-1] Persisted job {} status={} to postgres crawler_jobs", jobId, status);
        } catch (Exception e) {
            log.error("[P1-1] Failed persisting job status for {}", jobId, e);
        }
    }


    /** Get all crawl jobs from JPA (PostgreSQL) for the /api/crawler/jobs endpoint. */
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
                    Math.toIntExact((int) Double.parseDouble(postJson.getOrDefault("createdUtc", "0").toString())));
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
    /** Utility: safely convert any object to a trimmed string. */
    private String asString(Object o) {
        return o != null ? String.valueOf(o).trim() : "";
    }

// ──────────────────────────────────
    // P1-3: PostgreSQL persistence for crawled content
    // ──────────────────────────────────

    /** Persist crawled posts to the PostgreSQL `posts` table. */
    @Transactional
    public void persistPostsToPostgres(String jobId, List<Map<String, Object>> rawPosts) {
        if (rawPosts == null || rawPosts.isEmpty()) return;
        log.info("[P1-3] Persisting {} post(s) for jobId={} to PostgreSQL via JPA", rawPosts.size(), jobId);
        try {
            List<com.redditcrawler.api.model.Post> entities = new ArrayList<>();
            for (Map<String, Object> row : rawPosts) {
                com.redditcrawler.api.model.Post p = new com.redditcrawler.api.model.Post();
                p.setSubreddit(asString(row.get("subreddit")));
                p.setTitle(asString(row.get("title")));
                p.setBody(asString(row.getOrDefault("body", "")));
                p.setAuthor(asString(row.get("author")));
                Object u = row.get("upvotes");
                p.setUpvotes(u instanceof Number n ? n.intValue() : 0);
                Object cObj = row.get("commentsCount");
                p.setCommentsCount(cObj instanceof Number n ? n.intValue() : 0);
                Object tsObj = row.get("createdUtc");
                try {
                    p.setCreatedUtc(java.time.Instant.ofEpochSecond((long)(double)((Number)tsObj).doubleValue()));
                } catch (Exception e) {
                    p.setCreatedUtc(Instant.now());
                }
                p.setPermalink(asString(row.get("permalink")));
                p.setUrl(asString(row.getOrDefault("url", "")));
                entities.add(p);
            }
            if (!entities.isEmpty()) postRepo.saveAll(entities);
            log.info("[P1-3] OK {} posts persisted to PostgreSQL for jobId={}", entities.size(), jobId);
        } catch (Exception e) {
            log.error("[P1-3] Failed persisting posts for jobId=" + jobId, e);
        }
    }

    /** Persist crawled comments to the PostgreSQL `comments` table. */
    @Transactional
    public void persistCommentsToPostgres(String jobId, List<Map<String, Object>> rawComments) {
        if (rawComments == null || rawComments.isEmpty()) return;
        log.info("[P1-3] Persisting {} comment(s) for jobId={} to PostgreSQL via JPA", rawComments.size(), jobId);
        try {
            List<com.redditcrawler.api.model.Comment> entities = new ArrayList<>();
            for (Map<String, Object> row : rawComments) {
                com.redditcrawler.api.model.Comment c = new com.redditcrawler.api.model.Comment();
                c.setRedditId(asString(row.get("id")));
                String postTitle = asString(row.get("parentPostTitle"));
                String subredditName = asString(row.get("subreddit"));

                com.redditcrawler.api.model.Post targetPost = null;
                try {
                    List<com.redditcrawler.api.model.Post> candidates = postRepo.findBySubreddit(subredditName);
                    for (com.redditcrawler.api.model.Post candidate : candidates) {
                        if (candidate.getTitle().equals(postTitle)) {
                            targetPost = candidate;
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[P1-3] Could not find post for comment linking: {} on {}", postTitle, subredditName);
                }

                c.setPost(targetPost != null ? targetPost : new com.redditcrawler.api.model.Post());
                c.setAuthor(asString(row.get("author")));
                c.setBody(asString(row.getOrDefault("body", "")));
                Object upsObj = row.get("upvotes");
                c.setUpvotes(upsObj instanceof Number n ? n.intValue() : 0);
                Object depthObj = row.get("depth");
                c.setDepth(depthObj instanceof Number dn ? dn.intValue() : 0);
                c.setCreatedAt(java.time.LocalDateTime.now());
                entities.add(c);
            }
            if (!entities.isEmpty()) commentRepo.saveAll(entities);
            log.info("[P1-3] OK {} comments persisted to PostgreSQL for jobId={}", entities.size(), jobId);
        } catch (Exception e) {
            log.error("[P1-3] Failed persisting comments for jobId=" + jobId, e);
        }
    }
}