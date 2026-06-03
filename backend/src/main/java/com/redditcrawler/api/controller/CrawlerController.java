package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.RedditCrawlerService;
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
}
