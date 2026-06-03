package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.RedditCrawlerService;
import com.redditcrawler.api.service.RedditCrawlerService.PostDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for crawl job lifecycle operations.
 */
@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    private final RedditCrawlerService crawlerService;

    public CrawlerController(RedditCrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    /**
     * POST /api/crawler/start
     * Starts a new crawl job for the given subreddit.
     *
     * Request body: {"subreddit": "java", "limit": 50, ...}
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startCrawl(@RequestBody Map<String, Object> request) {
        String subreddit = (String) request.getOrDefault("subreddit", "");
        if (subreddit.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing 'subreddit' in request body"
            ));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> remaining = new LinkedHashMap<>(request);
        remaining.remove("subreddit");

        String jobId = crawlerService.startCrawl(subreddit, remaining.isEmpty() ? null : remaining);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", jobId);
        response.put("subreddit", subreddit);
        response.put("status", "RUNNING");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/crawler/status/{jobId}
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String jobId) {
        Map<String, Object> job = crawlerService.getStatus(jobId);
        if (job == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Job not found: " + jobId
            ));
        }

        // Build a copy with results attached
        @SuppressWarnings("unchecked")
        List<RedditCrawlerService.PostDTO> posts = RedditCrawlerService.PostDTO.fromResults(
                (List<Map<String, Object>>) job.get("resultsJson"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.putAll(job);
        response.put("posts", posts);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/crawler/stop/{jobId}
     */
    @DeleteMapping("/stop/{jobId}")
    public ResponseEntity<Map<String, Object>> stopCrawl(@PathVariable String jobId) {
        if (!crawlerService.getAllJobs().stream()
                .anyMatch(j -> ((String) j.get("jobId")).equals(jobId))) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Job not found: " + jobId
            ));
        }

        crawlerService.stopCrawl(jobId);
        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "status", "CANCELLED",
                "message", "Crawler job stopped"
        ));
    }

    // ------------------------------------------------------------------
    // GET /api/crawler/status  — frontend-facing active-status helper
    // ------------------------------------------------------------------
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        List<Map<String, Object>> jobs = crawlerService.getAllJobs();

        Optional<Map<String, Object>> runningJob = jobs.stream()
                .filter(j -> "RUNNING".equals(String.valueOf(j.get("status"))))
                .findFirst();

        if (runningJob.isPresent()) {
            Map<String, Object> job = runningJob.get();
            String jobId = String.valueOf(job.get("jobId"));
            Optional<PostDTO> posts = getFirstPostDTO((List<Map<String, Object>>) job.get("resultsJson"));

            Map<String, Object> response = new LinkedHashMap<>();
            response.putAll(job);
            response.put("posts", posts.map(List::of).orElse(List.of()));
            return ResponseEntity.ok(response);
        }

        // No active job — return empty idle state (frontend expects this shape)
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", (String) null);
        response.put("subreddit", "");
        response.put("status", "IDLE");
        response.put("progress", 0);
        response.put("posts", List.<PostDTO>of());
        return ResponseEntity.ok(response);
    }

    // ------------------------------------------------------------------
    // POST /api/crawler/stop  — frontend-facing stop-all helper
    // ------------------------------------------------------------------
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopAll() {
        List<Map<String, Object>> jobs = crawlerService.getAllJobs();
        long stopped = jobs.stream()
                .filter(j -> "RUNNING".equals(String.valueOf(j.get("status"))))
                .map(j -> String.valueOf(j.get("jobId")))
                .peek(crawlerService::stopCrawl)
                .count();

        return ResponseEntity.ok(Map.of(
                "stoppedCount", stopped,
                "message", stopped > 0 ? (stopped + " crawler(s) stopped") : "No active crawlers to stop"
        ));
    }

    private Optional<PostDTO> getFirstPostDTO(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) return Optional.empty();
        try {
            PostDTO dto = new PostDTO();
            Map<String, Object> first = results.get(0);
            dto.title = asString(first.get("title"));
            dto.body = asString(first.get("body"));
            dto.author = asString(first.get("author"));
            Object u = first.get("upvotes");
            dto.upvotes = u instanceof Number n ? n.intValue() : 0;
            Object c = first.get("commentsCount");
            dto.commentsCount = c instanceof Number n ? n.intValue() : 0;
            dto.permalink = asString(first.get("permalink"));
            dto.subreddit = asString(first.get("subreddit"));
            return Optional.of(dto);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String asString(Object o) {
        return o != null ? String.valueOf(o) : "";
    }
}
