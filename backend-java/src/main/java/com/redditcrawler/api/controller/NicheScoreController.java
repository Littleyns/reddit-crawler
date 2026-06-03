package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.NicheScorer;
import com.redditcrawler.api.service.RedditCrawlerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
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

    private Map<String, Object> doNiche(String subreddit) {
        for (var job : crawlerService.getAllJobs()) {
            if (subreddit.equalsIgnoreCase((String) job.get("subreddit"))) {
                @SuppressWarnings("unchecked")
                List<RedditCrawlerService.PostDTO> posts = RedditCrawlerService.PostDTO.fromResults(
                        (List<Map<String, Object>>) job.get("resultsJson"));

                if (!posts.isEmpty()) {
                    Map<String, Double> scores = nicheScorer.score(posts);
                    return Map.of("subreddit", subreddit, "niche", scores, "source", "cached_crawl");
                }
            }
        }
        String jobId = crawlerService.startCrawl(subreddit, Map.of("limit", 25));
        return Map.of("subreddit", subreddit, "niche", Map.<String, Double>of(), "source", "crawl_in_progress", "jobId", jobId);
    }

    /**
     * GET /api/niche/score?subreddit=X — query-param form.
     */
    @GetMapping("/score")
    public ResponseEntity<Map<String, Object>> scoreNiche(@RequestParam String subreddit) {
        return ResponseEntity.ok(doNiche(subreddit));
    }

    /**
     * GET /api/niche-score/{subreddit}  — matches frontend API paths (path variable syntax).
     */
    @GetMapping("{subreddit}")
    public ResponseEntity<Map<String, Object>> pathScoreNiche(@PathVariable String subreddit) {
        return ResponseEntity.ok(doNiche(subreddit));
    }
}
