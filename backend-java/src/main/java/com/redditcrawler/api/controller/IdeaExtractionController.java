package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.IdeaExtractionService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
 * REST controller for the Idea Extraction Engine.
 * Provides /api/ideas endpoints — extract structured ideas from Reddit thread text.
 */
@RestController
@RequestMapping("/api/ideas")
@CrossOrigin(origins = "*")
public class IdeaExtractionController {

    private final IdeaExtractionService ideaExtractionService;

    public IdeaExtractionController(IdeaExtractionService ideaExtractionService) {
        this.ideaExtractionService = ideaExtractionService;
    }

    /**
     * POST /api/ideas — Extract ideas from one or more texts.
     * Accepts JSON: { "texts": ["post body 1", "post body 2", ...] }
     */
    @PostMapping("/extract")
    public ResponseEntity<List<IdeaExtractionService.Idea>> extractIdeas(@RequestBody IdeaRequest request) {
        if (request.texts() == null || request.texts().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // Single-text fast path
        if (request.texts().size() == 1 && request.batchMode() != null && request.batchMode()) {
            List<IdeaExtractionService.Idea> ideas = ideaExtractionService.extractFromThread(request.texts());
            return ResponseEntity.ok(ideas);
        }

        // Fallback: process each text individually, then combine
        List<IdeaExtractionService.Idea> merged = new java.util.ArrayList<>();
        for (String text : request.texts()) {
            merged.addAll(ideaExtractionService.extractIdeas(text));
        }

        return ResponseEntity.ok(merged);
    }

    /**
     * GET /api/ideas — Quick extract from query parameter.
     * Example: POST to extract, or single-text via body.
     */
    @GetMapping("/quick")
    public ResponseEntity<List<IdeaExtractionService.Idea>> quickExtract(
            @RequestParam @NotBlank @Size(max = 5000) String text) {

        List<IdeaExtractionService.Idea> ideas = ideaExtractionService.extractIdeas(text);
        return ResponseEntity.ok(ideas);
    }

    /**
     * Request body for POST /api/ideas/extract.
     */
    public record IdeaRequest(
            List<String> texts,
            Boolean batchMode
    ) {
        public IdeaRequest {
            if (texts == null) texts = java.util.List.of();
            if (batchMode == null) batchMode = false;
        }
    }

    /**
     * Response wrapper for idea extraction.
     */
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
                for (var id : ideas) {
                    breakdown.merge(id.category(), 1, Integer::sum);
                }
                return new IdeasResponse(ideas.size(), ideas, breakdown);
            }
        }

        public static ClassBuilder builder(List<IdeaExtractionService.Idea> ideas) {
            return new ClassBuilder(ideas);
        }
    }
}
