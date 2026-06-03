package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.NicheScorer;
import com.redditcrawler.api.service.RedditCrawlerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller providing niche scoring endpoints.
 */
@RestController
@RequestMapping("/api/niche")
public class NicheScoreController {

    private final NicheScorer nicheScorer;
    private final RedditCrawlerService crawlerService;

    public NicheScoreController(NicheScorer nicheScorer, RedditCrawlerService crawlerService) {
        this.nicheScorer = nicheScorer;
        this.crawlerService = crawlerService;
    }

    /**
     * GET /api/niche/score?subreddit=X
     * Returns a map of niche scores if available, otherwise triggers a crawl first.
     */
    @GetMapping("/score")
    public ResponseEntity<Map<String, Object>> scoreNiche(@RequestParam String subreddit) {
        // First try to get existing crawl results
        for (var job : crawlerService.getAllJobs()) {
            if (subreddit.equalsIgnoreCase(job.get("subreddit"))) {
                @SuppressWarnings("unchecked")
                List<RedditCrawlerService.PostDTO> posts = RedditCrawlerService.PostDTO.fromResults(
                        (List<Map<String, Object>>) job.get("resultsJson"));

                if (!posts.isEmpty()) {
                    Map<String, Double> scores = nicheScorer.score(posts);
                    return ResponseEntity.ok(Map.of(
                            "subreddit", subreddit,
                            "niche", scores,
                            "source", "cached_crawl"
                    ));
                }
            }
        }

        // If no results are cached, trigger a quick crawl + score
        String jobId = crawlerService.startCrawl(subreddit, Map.of("limit", 25));
        return ResponseEntity.ok(Map.of(
                "subreddit", subreddit,
                "niche", Map.<String, Double>of(),
                "source", "crawl_in_progress",
                "jobId", jobId
        ));
    }
}
