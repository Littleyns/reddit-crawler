package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.SummarizationService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the Text Summarization Engine.
 * Provides extractive summarization endpoints over crawled Reddit content
 * and arbitrary user-supplied texts.
 *
 * Example endpoints:
 *   POST /api/summarize                → summarize submitted text
 *   GET  /api/summarize/saved?idx=0    → summarize crawled post at index
 *   GET  /api/summarize?text=&n=5      → quick single-text summary
 */
@RestController
@RequestMapping("/api/summarize")
@CrossOrigin(origins = "*")
public class SummarizationController {

    private final SummarizationService summarizationService;

    public SummarizationController(SummarizationService summarizationService) {
        this.summarizationService = summarizationService;
    }

    /**
     * POST /api/summarize — Summarize submitted text.
     * Accepts JSON: { "text": "...", "numSentences": 5 }
     */
    @PostMapping("")
    public ResponseEntity<Map<String, Object>> summarize(
            @RequestBody(required = false) SummaryRequest request) {

        if (request == null || request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No text provided"));
        }

        String summary = summarizationService.summarize(request.text(), request.numSentences());
        Map<String, Object> response = java.util.Map.of(
                "summary", summary,
                "totalSentences", countSentences(summary),
                "originalLength", request.text().length()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/summarize/quick?text=...&n=5 — One-line extractive summary.
     */
    @GetMapping("/quick")
    public ResponseEntity<Map<String, Object>> quickSummarize(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "1") int n) {

        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No text provided"));
        }

        String summary = summarizationService.summarize(text, n);
        Map<String, Object> response = java.util.Map.of(
                "summary", summary,
                "numSentences", n,
                "originalLength", text.length()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/summarize/saved?idx=5 — Summarize a crawled post by index.
     */
    @GetMapping("/saved")
    public ResponseEntity<Map<String, Object>> summarizeCrawled(
            @RequestParam int idx,
            @RequestParam(defaultValue = "0") String key) {

        // Fetch all posts from crawler service (same pattern as other controllers)
        try {
            java.lang.reflect.Method getAllJobs = Class.forName("com.redditcrawler.api.service.RedditCrawlerService")
                    .getMethod("getAllJobs");
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> jobs =
                    (java.util.List<java.util.Map<String, Object>>) getAllJobs.invoke(
                            com.redditcrawler.api.service.RedditCrawlerService.class.getDeclaredConstructor().newInstance());

            // Extract all body text from completed jobs
            List<String> bodies = new java.util.ArrayList<>();
            for (Map<String, Object> job : jobs) {
                if (!"COMPLETED".equals(String.valueOf(job.get("status")))) continue;
                Object results = job.get("resultsJson");
                if (results instanceof java.util.List<?> list) {
                    for (Object row : list) {
                        if (row instanceof java.util.Map map) {
                            Object val = map.get("body");
                            bodies.add(val != null ? String.valueOf(val) : "");
                        }
                    }
                }
            }

            if (bodies.isEmpty()) {
                return ResponseEntity.ok(Map.of("error", "No crawled data available"));
            }
            int safeIdx = Math.max(0, Math.min(idx, bodies.size() - 1));
            String bodyText = "<title> " + bodies.get(safeIdx) + "\n\n<hits>";

            // Also grab surrounding context posts for richer summary
            List<String> ctx = java.util.List.copyOf(bodies);
            int start = Math.max(0, safeIdx - 2);
            int end = Math.min(ctx.size(), safeIdx + 3);
            String combinedCtx = ctx.subList(start, end).stream()
                    .collect(java.util.stream.Collectors.joining("\n\n"));

            int nSentences = Math.max(1, (int) Math.ceil(bodies.get(safeIdx).split("\\.").length * 0.5));

            String summary = summarizationService.summarize(combinedCtx, nSentences);
            return ResponseEntity.ok(Map.of(
                    "summary", summary,
                    "postIndex", safeIdx,
                    "totalPosts", bodies.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Could not load crawled data: " + e.getMessage()));
        }
    }

    /**
     * GET /api/summarize/scores?text=... — Get sentence-level scores for inspection.
     */
    @GetMapping("/scores")
    public ResponseEntity<List<Map<String, Object>>> getScores(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "5") int n) {

        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.List.of());
        }

        List<Map<String, Object>> scores = summarizationService.summarizeWithScores(text, n);
        return ResponseEntity.ok(scores);
    }

    // ---- helpers ---------------------------------------------------------------

    private int countSentences(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '.' && (i + 1 >= text.length() || text.charAt(i + 1) <= ' ')) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    // ---- request DTO -----------------------------------------------------------

    public record SummaryRequest(String text, int numSentences) {
        public SummaryRequest {
            if (numSentences <= 0) numSentences = 5;
        }
    }
}

