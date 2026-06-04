package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.SentimentAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing sentiment analysis endpoints under /api/llm/sentiment.
 * Backed by SentimentAnalysisService (VADER-style lexicon + optional LLM).
 */
@RestController
@RequestMapping("/api/llm/sentiment")
public class SentimentController {

    private final SentimentAnalysisService sentimentAnalysisService;

    public SentimentController(SentimentAnalysisService sentimentAnalysisService) {
        this.sentimentAnalysisService = sentimentAnalysisService;
    }

    /**
     * POST /api/llm/sentiment/single
     * Analyze one text block's sentiment (VADER + optional LLM).
     */
    @PostMapping("/single")
    public ResponseEntity<Map<String, Object>> analyzeSingle(
            @RequestBody(required = true) Map<String, String> body) {
        String text = body != null ? body.getOrDefault("text", "") : "";

        Map<String, Object> llmResult = sentimentAnalysisService.analyzeViaLLM(text);

        // Always return VADER result; LLM result is included when available
        SentimentAnalysisService.SentimentScore vader = sentimentAnalysisService.analyze(text);

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("sentiment", vader.sentiment());
        response.put("confidence", vader.confidence());
        response.put("positiveCount", vader.positiveCount());
        response.put("negativeCount", vader.negativeCount());
        response.put("wordCount", vader.wordCount());
        if (llmResult != null) {
            response.put("llmSentiment", llmResult.get("sentiment"));
            response.put("llmConfidence", llmResult.get("confidence"));
            response.put("llmReason", llmResult.get("reason"));
            response.put("provider", "llm");
        } else {
            response.put("provider", "vader");
            response.put("note", "LLM not configured, VADER lexicon only");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/llm/sentiment/summary?text1=foo&text2=bar
     * Quick summary of overall sentiment across queries as params.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(required = false) List<String> text) {
        if (text == null || text.isEmpty()) {
            // Return default 0-summary with note
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("positive", 0);
            response.put("negative", 0);
            response.put("neutral", 0);
            response.put("total", 0);
            response.put("overallBalance", 0);
            response.put("note", "Provide 'text' query params for batch summary");
            return ResponseEntity.ok(response);
        }

        var results = sentimentAnalysisService.summarize(text);
        // Flatten the first overview map as top-level keys
        Map<String, Object> overview = (Map<String, Object>) results.get(0);
        Map<String, Object> response = new java.util.LinkedHashMap<>(overview);
        response.put("individualResults", results.size() > 1 ? ((List<Map<String, Object>>) results.get(1)).size() : "none");
        return ResponseEntity.ok(response);
    }
}
