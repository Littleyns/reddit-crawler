package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.TextAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing NLP analytics endpoints backed by TextAnalysisService.
 */
@RestController
@RequestMapping("/api/analysis")
public class LlmAnalysisController {

    private final TextAnalysisService textAnalysisService;

    public LlmAnalysisController(TextAnalysisService textAnalysisService) {
        this.textAnalysisService = textAnalysisService;
    }

    /**
     * GET /api/analysis/sentiment
     * Sentiment scores for all crawled posts.
     */
    @GetMapping("/sentiment")
    public ResponseEntity<List<Map<String, Object>>> getSentiment() {
        return ResponseEntity.ok(textAnalysisService.analyzeSentimentBySubreddit());
    }

    /**
     * GET /api/analysis/ideas
     * Extracted project ideas (LLM-enhanced or heuristic).
     */
    @GetMapping("/ideas")
    public ResponseEntity<List<Map<String, Object>>> getIdeas() {
        return ResponseEntity.ok(textAnalysisService.extractIdeas());
    }

    /**
     * GET /api/analysis/trends
     * Subreddit trends sorted by engagement velocity.
     */
    @GetMapping("/trends")
    public ResponseEntity<List<Map<String, Object>>> getTrends() {
        return ResponseEntity.ok(textAnalysisService.getSubredditTrends());
    }

    /**
     * GET /api/analysis/keywords?topN=30
     * Top keyword frequencies for word-cloud display.
     */
    @GetMapping("/keywords")
    public ResponseEntity<List<Map<String, Object>>> getKeywords(
            @RequestParam(defaultValue = "30") int topN) {
        return ResponseEntity.ok(textAnalysisService.getKeywordFrequencies(topN));
    }

    /**
     * GET /api/analysis/heatmap
     * Sentiment heatmap: positive/neutral/negative per subreddit.
     */
    @GetMapping("/heatmap")
    public ResponseEntity<List<Map<String, Object>>> getHeatmap() {
        return ResponseEntity.ok(textAnalysisService.getSentimentHeatmapData());
    }

    /**
     * GET /api/analysis/report
     * Full analytics report combining all data sources.
     */
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getReport() {
        return ResponseEntity.ok(textAnalysisService.getFullAnalyticsReport());
    }

    /**
     * GET /api/analysis/subreddit/{subreddit}
     * Summarize a specific subreddit: tone, breakdown, keywords, ideas.
     */
    @GetMapping("/subreddit/{subreddit}")
    public ResponseEntity<Map<String, Object>> getSubredditSummary(
            @PathVariable String subreddit) {
        return ResponseEntity.ok(textAnalysisService.summarizeSubreddit(subreddit));
    }
}
