package com.redditcrawler.api.controller;

import com.redditcrawler.api.dto.IdeaItem;
import com.redditcrawler.api.model.CrawlerJob;
import com.redditcrawler.api.repository.CrawlerJobRepository;
import com.redditcrawler.api.service.IdeaExtractionService;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the Idea Extraction Engine.
 * Provides endpoints for both heuristic extraction and LLM-powered analysis of crawler job threads.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class IdeaExtractionController {

    private final IdeaExtractionService ideaExtractionService;
    private final CrawlerJobRepository crawlerJobRepository;

    public IdeaExtractionController(
            IdeaExtractionService ideaExtractionService,
            CrawlerJobRepository crawlerJobRepository) {
        this.ideaExtractionService = ideaExtractionService;
        this.crawlerJobRepository = crawlerJobRepository;
    }

    // =========================================================================
    // NEW: LLM-powered analysis endpoint
    // POST /api/crawler/analysis/{crawlerJobId}
    // Body: { "prompt": "...", "modelConfig": {...}, "maxIdeas": 10 }
    // Returns: List<IdeaItem> with relevance scores, sentiment, keywords
    // =========================================================================

    /**
     * Analyze Reddit threads associated with a crawler job using LLM-powered idea extraction.
     * Gathers post bodies from the job's results and passes them through extractIdeaList().
     */
    @PostMapping("/crawler/analysis/{crawlerJobId}")
    public ResponseEntity<List<IdeaItem>> analyzeCrawlerJob(
            @PathVariable String crawlerJobId,
            @RequestBody AnalysisRequest request) {

        // 1. Look up the crawler job
        CrawlerJob job = crawlerJobRepository.findById(crawlerJobId).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        // 2. Gather post content from the job's results
        String threadContents = buildThreadContext(job, request.prompt());

        // 3. Build/merge model config with defaults
        Map<String, Object> modelConfig;
        if (request.modelConfig() != null && !request.modelConfig().isEmpty()) {
            modelConfig = new java.util.LinkedHashMap<>(request.modelConfig());
        } else {
            modelConfig = new java.util.LinkedHashMap<>();
        }
        modelConfig.putIfAbsent("model", "qwen3.6:35b");

        // 4. Extract ideas via LLM through the service
        int maxIdeas = request.maxIdeas() != null && request.maxIdeas() > 0 ? request.maxIdeas() : 10;

        try {
            List<IdeaItem> ideas = ideaExtractionService.extractIdeaList(threadContents, modelConfig, maxIdeas);
            return ResponseEntity.ok(ideas);
        } catch (Exception e) {
            // Log and return empty list so the frontend can still display a message
            java.util.logging.Logger.getLogger(IdeaExtractionController.class.getName())
                    .severe("Idea extraction failed for job " + crawlerJobId + ": " + e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    // =========================================================================
    // Existing heuristic extraction endpoints (backward-compatible)
    // =========================================================================

    /** POST /api/ideas/extract — Extract ideas from one or more texts using heuristics. */
    @PostMapping("/ideas/extract")
    public ResponseEntity<List<IdeaExtractionService.Idea>> extractIdeas(
            @RequestBody IdeaRequest request) {
        if (request.texts() == null || request.texts().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<IdeaExtractionService.Idea> ideas;
        if (request.texts().size() == 1 && Boolean.FALSE.equals(request.batchMode())) {
            ideas = ideaExtractionService.extractIdeas(request.texts().get(0));
        } else {
            ideas = ideaExtractionService.extractFromThread(request.texts());
        }
        return ResponseEntity.ok(ideas);
    }

    /** GET /api/ideas/quick/{text} — Quick extract from query parameter. */
    @GetMapping("/ideas/quick")
    public ResponseEntity<List<IdeaExtractionService.Idea>> quickExtract(
            @RequestParam @NotBlank(message = "text cannot be blank") String text) {
        List<IdeaExtractionService.Idea> ideas = ideaExtractionService.extractIdeas(text);
        return ResponseEntity.ok(ideas);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Build a combined context prompt from the crawler job's results JSON. */
    private String buildThreadContext(CrawlerJob job, String userPrompt) {
        String header = "Reddit threads from crawler job [" + job.getJobId()
                + "] on subreddit r/" + job.getSubreddit()
                + ". Extract structured ideas with relevance scores and sentiment analysis.\n\n";

        String resultsJson = job.getResultsJson();
        if (resultsJson != null && !resultsJson.isBlank()) {
            return header + "Raw thread data:\n" + resultsJson;
        }

        // No data yet — instruct what to do with the prompt
        return header + "No thread data is available for this job yet. "
                + "Please populate the crawler job results first.\n\n"
                + "Based on the prompt below, describe which types of ideas to extract:\n"
                + userPrompt;
    }

    // =========================================================================
    // Inner DTOs
    // =========================================================================

    /** Request body for POST /api/crawler/analysis/{id}. */
    public record AnalysisRequest(
            @NotBlank(message = "prompt cannot be empty")
            String prompt,

            Map<String, Object> modelConfig,

            Integer maxIdeas
    ) {
        public AnalysisRequest {
            if (prompt == null || prompt.isBlank()) {
                throw new IllegalArgumentException("prompt cannot be blank");
            }
        }
    }

    /** Request body for POST /api/ideas/extract. */
    public record IdeaRequest(
            List<String> texts,
            Boolean batchMode
    ) {
        public IdeaRequest {
            if (texts == null) texts = List.of();
            if (batchMode == null) batchMode = false;
        }
    }

    /** Response wrapper for heuristic idea extraction. */
    public record IdeasResponse(
            int totalIdeas,
            List<IdeaExtractionService.Idea> ideas,
            Map<String, Integer> categoryBreakdown
    ) {
        public static class ClassBuilder {
            private final List<IdeaExtractionService.Idea> ideas;

            public ClassBuilder(List<IdeaExtractionService.Idea> ideas) { this.ideas = ideas; }

            public IdeasResponse build() {
                var breakdown = new java.util.LinkedHashMap<String, Integer>();
                for (var i : ideas) {
                    breakdown.merge(i.category(), 1, Integer::sum);
                }
                return new IdeasResponse(ideas.size(), ideas, breakdown);
            }
        }

        public static ClassBuilder builder(List<IdeaExtractionService.Idea> ideas) {
            return new ClassBuilder(ideas);
        }
    }
}
