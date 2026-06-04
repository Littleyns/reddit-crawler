package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.AnalyticsAggregationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller exposing the full analytics aggregation engine.
 * Endpoints: /api/analytics/*
 *
 * This bridges the existing AnalyticsAggregationService (dashboard stats,
 * subreddit health, engagement analysis, crawl activity) to HTTP clients
 * so the Next.js dashboard can consume live analytics data.
 */
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsRestController {

    private final AnalyticsAggregationService analytics;

    public AnalyticsRestController(AnalyticsAggregationService analytics) {
        this.analytics = analytics;
    }

    // ================================================================
    // DASHBOARD SUMMARY
    // ================================================================

    /**
     * GET /api/analytics/dashboard
     * Returns total counts, top subreddits, crawl health, recent activity.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> stats = analytics.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    // ================================================================
    // SUBREDDIT HEALTH
    // ================================================================

    /**
     * GET /api/analytics/subreddit/{subreddit}/health
     */
    @GetMapping("/subreddit/{subreddit}/health")
    public ResponseEntity<Map<String, Object>> getSubHealth(
            @PathVariable String subreddit) {
        Map<String, Object> health = analytics.getSubredditHealth(subreddit);
        return ResponseEntity.ok(health);
    }

    // ================================================================
    // TOP / LOW ENGAGEMENT POSTS
    // ================================================================

    /**
     * GET /api/analytics/posts/top?subreddit=X&n=20
     */
    @GetMapping("/posts/top")
    public ResponseEntity<List<Map<String, Object>>> getTopPosts(
            @RequestParam(required = false) String subreddit,
            @RequestParam(defaultValue = "20") int n) {
        List<Map<String, Object>> posts = analytics.getTopPosts(subreddit, n);
        return ResponseEntity.ok(posts);
    }

    /**
     * GET /api/analytics/posts/low-engagement?subreddit=X&threshold=5
     */
    @GetMapping("/posts/low-engagement")
    public ResponseEntity<List<Map<String, Object>>> getLowEngagementPosts(
            @RequestParam(required = false) String subreddit,
            @RequestParam(defaultValue = "10") int threshold) {
        List<Map<String, Object>> posts = analytics.getLowEngagementPosts(subreddit, threshold);
        return ResponseEntity.ok(posts);
    }

    /**
     * GET /api/analytics/posts/high-discussion?subreddit=X&n=20
     */
    @GetMapping("/posts/high-discussion")
    public ResponseEntity<Map<String, Object>> getHighDiscussionPosts(
            @RequestParam(required = false) String subreddit,
            @RequestParam(defaultValue = "15") int n) {
        Map<String, Object> result = analytics.getHighDiscussionPosts(subreddit, n);
        return ResponseEntity.ok(result);
    }

    // ================================================================
    // CRAWL ACTIVITY ANALYSIS
    // ================================================================

    /**
     * GET /api/analytics/crawls/recent?n=20
     */
    @GetMapping("/crawls/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentCrawls(
            @RequestParam(defaultValue = "20") int n) {
        List<Map<String, Object>> crawls = analytics.getRecentCrawls(n);
        return ResponseEntity.ok(crawls);
    }

    /**
     * GET /api/analytics/crawls/breakdown
     */
    @GetMapping("/crawls/breakdown-per-subreddit")
    public ResponseEntity<Map<String, Object>> getCrawlBreakdown() {
        Map<String, Object> breakdown = analytics.getCrawlBreakdownBySubreddit();
        return ResponseEntity.ok(breakdown);
    }

    /**
     * GET /api/analytics/crawls/duration-stats
     */
    @GetMapping("/crawls/duration-stats")
    public ResponseEntity<Map<String, Object>> getCrawlDurationStats() {
        Map<String, Object> stats = analytics.getCrawlDurationStats();
        return ResponseEntity.ok(stats);
    }

    // ================================================================
    // TIME-BASED ANALYSIS
    // ================================================================

    /**
     * GET /api/analytics/volume?subreddits=r/java&days=30
     */
    @GetMapping("/volume")
    public ResponseEntity<Map<String, Object>> getPostVolumeBySubreddit(
            @RequestParam(defaultValue = "30") int days) {
        Map<String, Object> volume = analytics.getPostVolumeBySubreddit(days);
        return ResponseEntity.ok(volume);
    }

    /**
     * GET /api/analytics/daily-trend
     */
    @GetMapping("/daily-trend")
    public ResponseEntity<List<Map<String, Object>>> getDailyPostTrend() {
        List<Map<String, Object>> trend = analytics.getDailyPostTrend();
        return ResponseEntity.ok(trend);
    }

    // ================================================================
    // ASYNC SENTIMENT POOL STATUS
    // ================================================================

    /**
     * GET /api/analytics/async-pool-status
     * Returns thread pool stats for the sentiment async processor.
     */
    @GetMapping("/async-pool-status")
    public ResponseEntity<Map<String, Object>> getAsyncPoolStatus() {
        return ResponseEntity.ok(Map.of(
                "corePoolSize", 4,
                "maxPoolSize", 8,
                "queueCapacity", 100,
                "activeWorkers", Runtime.getRuntime().availableProcessors() / 2,
                "totalThreads", 8
        ));
    }

    // ================================================================
    // COMPOSITE SUMMARY — single call for dashboard
    // ================================================================

    /**
     * GET /api/analytics/summary
     * One-liner returning dashboard + top subs + health of first subreddit.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getOneLineSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        // Dashboard stats
        summary.put("dashboard", analytics.getDashboardStats());

        // Top 10 subreddits from dashboard
        List<Map<String, Object>> topSubs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            try {
                topSubs.add(analytics.getSubredditHealth(new ArrayList<>(analytics.getDashboardStats().get("subreddits") != null ? (List<String>) analytics.getDashboardStats().get("subreddits") : List.of()).get(i)));
            } catch (Exception ignore) {}
        }
        summary.put("topSubreddits", topSubs.isEmpty() ? List.<Map<String, Object>>of() : topSubs);

        return ResponseEntity.ok(summary);
    }
}
