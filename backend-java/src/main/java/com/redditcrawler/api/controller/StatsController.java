package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.RedditCrawlerService;
import com.redditcrawler.api.service.NicheScorer;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for dashboard stats endpoints.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final RedditCrawlerService crawlerService;
    private final NicheScorer nicheScorer;

    public StatsController(RedditCrawlerService crawlerService, NicheScorer nicheScorer) {
        this.crawlerService = crawlerService;
        this.nicheScorer = nicheScorer;
    }

    // -----------------------------------------------------------------
    // GET /api/stats
    // -----------------------------------------------------------------
    @GetMapping("")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<Map<String, Object>> jobs = crawlerService.getAllJobs();

        int totalPosts = 0;
        int totalSessions = jobs.size();
        var subredditSet = new ArrayList<String>();

        for (Map<String, Object> job : jobs) {
            String status = String.valueOf(job.get("status"));
            if (!"COMPLETED".equals(status)) continue;

            // Parse resultsJson as before (same approach as DataController)
            Object obj = job.get("resultsJson");
            if (obj instanceof List<?> list) {
                totalPosts += list.size();
                String sub = String.valueOf(job.get("subreddit"));
                if (!subredditSet.contains(sub)) {
                    subredditSet.add(sub);
                }
            }
        }

        int successRate = totalSessions > 0
                ? (int) (jobs.stream().filter(j -> "COMPLETED".equals(String.valueOf(j.get("status")))).count() * 100.0 / totalSessions)
                : 0;

        // Build activity log from jobs
        List<Map<String, Object>> activities = new ArrayList<>();
        for (int i = Math.max(0, jobs.size() - 5); i < jobs.size(); i++) {
            Map<String, Object> job = jobs.get(i);
            String status = String.valueOf(job.get("status"));
            String subreddit = String.valueOf(job.get("subreddit"));

            Map<String, Object> activity = new LinkedHashMap<>();
            activity.put("id", "act-" + i);
            activity.put("title", "Crawl: r/" + subreddit);
            activity.put("description", status);
            activity.put("occurredAt", job.getOrDefault("startedAt", ""));
            if ("COMPLETED".equals(status)) {
                activity.put("status", "success");
            } else if ("RUNNING".equals(status)) {
                activity.put("status", "running");
            } else if ("FAILED_NO_DATA".equals(status) || status.startsWith("FAILED:")) {
                activity.put("status", "error");
            } else {
                activity.put("status", "warning");
            }
            activities.add(activity);
        }

        List<String> subsList = subredditSet.isEmpty() ? List.of("n/a") : subredditSet;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalPosts", totalPosts);
        stats.put("totalComments", 0);
        stats.put("totalSessions", totalSessions);
        stats.put("activeSubreddits", subredditSet.size());
        stats.put("successRate", successRate);
        stats.put("queueDepth", crawlerService.getPendingQueueLength() > 0 ? crawlerService.getPendingQueueLength() : 0);
        stats.put("activities", activities);
        stats.put("subreddits", subsList);

        return ResponseEntity.ok(stats);
    }
}
