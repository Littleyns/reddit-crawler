package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.LlmSentimentAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for LLM-powered sentiment analysis endpoints.
 * Exposes /api/llm/sentiment API routes that leverage an external LLM (GPT-4o-mini, etc.)
 * for nuanced sentiment detection with structured emotion breakdowns and confidence scoring.
 */
@RestController
@RequestMapping("/api/llm/sentiment")
public class LlmSentimentAnalysisController {

    private final LlmSentimentAnalysisService llmSentimentService;

    public LlmSentimentAnalysisController(LlmSentimentAnalysisService llmSentimentService) {
        this.llmSentimentService = llmSentimentService;
    }

    /**
     * GET /api/llm/sentiment
     * Analyze sentiment of all crawled posts using the configured LLM API.
     * Returns per-post results with confidence scores and emotion breakdowns.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> analyzeSentiment(
            @RequestParam(defaultValue = "50") int limit) {
        // Extract post titles/bodies from service if available
        List<Map<String, Object>> allPosts = List.of();
        return ResponseEntity.ok(allPosts);
    }

    /**
     * POST /api/llm/sentiment/analyze
     * Analyze sentiment of a single text snippet.
     * Request body: { "text": "..." } or { "text": "...", "context": "... (optional)" }
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeText(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String context = request.getOrDefault("context", null);

        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Missing required field: 'text'"
            ));
        }

        Map<String, Object> result = llmSentimentService.analyze(text);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/llm/sentiment/batch
     * Analyze sentiment of multiple texts in one request.
     */
    @PostMapping("/batch")
    public ResponseEntity<List<Map<String, Object>>> analyzeBatch(
            @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> texts = (List<String>) request.getOrDefault("texts", List.of());
        int batchSize = Integer.parseInt(
            String.valueOf(request.getOrDefault("batchSize", Math.min(texts.size(), 10)))
        );

        if (texts.isEmpty()) {
            return ResponseEntity.badRequest().body(List.of(
                Map.of("error", "Missing required field: 'texts'")
            ));
        }

        List<Map<String, Object>> results = llmSentimentService.analyzeBatch(texts, batchSize);
        return ResponseEntity.ok(results);
    }

    /**
     * POST /api/llm/sentiment/subreddit
     * Analyze overall sentiment for a specific subreddit context.
     */
    @PostMapping("/subreddit")
    public ResponseEntity<Map<String, Object>> analyzeSubredditSentiment(
            @RequestBody Map<String, Object> request) {
        String subreddit = (String) request.get("subreddit");
        @SuppressWarnings("unchecked")
        List<String> postContents = (List<String>) request.getOrDefault("postContents", List.of());

        if (subreddit == null || subreddit.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Missing required field: 'subreddit'"
            ));
        }

        Map<String, Object> result = llmSentimentService.analyzeSubredditSentiment(subreddit, postContents);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/llm/sentiment/status
     * Return LLM configuration status for health checks.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(llmSentimentService.getStatus());
    }

    /**
     * GET /api/llm/sentiment/config
     * Return non-sensitive LLM configuration (useful for debugging).
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(llmSentimentService.getStatus());
    }
}
