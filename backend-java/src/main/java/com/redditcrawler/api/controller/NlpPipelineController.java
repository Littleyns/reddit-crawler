package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.NlpPipelineService;
import com.redditcrawler.api.service.RedditCrawlerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST endpoints for the Java-based NLP pipeline: TF-IDF keyword extraction,
 * LDA-like topic modeling, and clustered idea suggestions.
 *
 * <p>All algorithms are implemented in pure Java -- no external ML dependencies needed.
 *
 * Example endpoints:
 *   GET /api/nlp/tfidf?topN=30           → corpus TF-IDF keywords
 *   GET /api/nlp/tfidf/doc/0?topN=20     → TF-IDF for document at index 0 (requires ?idx)
 *   GET /api/nlp/topics?k=5              → LDA topic discovery results
 *   GET /api/nlp/cluster-ideas?k=3       → clustered idea suggestions per topic
 */
@RestController
@RequestMapping("/api/nlp")
public class NlpPipelineController {

    private final RedditCrawlerService crawlerService;
    private final NlpPipelineService nlpPipelineService;

    public NlpPipelineController(
            RedditCrawlerService crawlerService,
            NlpPipelineService nlpPipelineService) {
        this.crawlerService = crawlerService;
        this.nlpPipelineService = nlpPipelineService;
    }

    // ---- corpus helpers ---------------------------------------------------

    /** Collect all crawled posts into a list of Map&lt;title, body&gt; maps. */
    private List<Map<String, String>> collectCorpus() {
        List<Map<String, String>> corpus = new ArrayList<>();
        for (Map<String, Object> job : crawlerService.getAllJobs()) {
            Object statusObj = job.get("status");
            if (!"COMPLETED".equals(String.valueOf(statusObj))) continue;
            Object obj = job.get("resultsJson");
            if (!(obj instanceof List<?> list)) continue;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultsList = (List<Map<String, Object>>) obj;
            for (Map<String, Object> row : resultsList) {
                Map<String, String> doc = new LinkedHashMap<>();
                doc.put("title", asString(row.get("title")));
                doc.put("body",    asString(row.get("body")));
                corpus.add(doc);
            }
        }
        return corpus;
    }

    private String asString(Object o) {
        return o != null ? String.valueOf(o) : "";
    }

    // ---- endpoints --------------------------------------------------------

    /**
     * GET /api/nlp/tfidf
     * Corpus-wide TF-IDF keyword extraction.
     * Computes term frequency across the entire Reddit corpus, normalizes with
     * document-frequency-based inverse IDF, and returns top-N terms ranked by
     * average TF-IDF score.
     */
    @GetMapping("/tfidf")
    public ResponseEntity<List<Map<String, Object>>> tfidfCorpus(
            @RequestParam(defaultValue = "30") int topN) {
        List<Map<String, String>> corpus = collectCorpus();
        return ResponseEntity.ok(nlpPipelineService.tfidfKeywords(corpus, topN));
    }

    /**
     * GET /api/nlp/tfidf/doc?idx=0&topN=20
     * TF-IDF scores for a document identified by its positional index in the crawled corpus.
     */
    @GetMapping("/tfidf/doc")
    public ResponseEntity<List<Map<String, Object>>> tfidfSingleDoc(
            @RequestParam int idx,
            @RequestParam(defaultValue = "20") int topN) {
        List<Map<String, String>> corpus = collectCorpus();
        if (idx < 0 || idx >= corpus.size()) {
            return ResponseEntity.badRequest().body(List.of(Map.of(
                    "error", "Document index out of range: " + idx + " (corpus size: " + corpus.size() + ")"
            )));
        }
        return ResponseEntity.ok(nlpPipelineService.tfidfForDocument(corpus, idx, topN));
    }

    /**
     * GET /api/nlp/topics?k=5
     * LDA-style topic modeling. Discovers K latent topics in the crawled posts using
     * Gibbs sampling with Dirichlet priors (alpha=50, beta=0.01). Returns each topic's
     * top words with their estimated probability mass and cluster document count.
     */
    @GetMapping("/topics")
    public ResponseEntity<List<Map<String, Object>>> ldaTopics(
            @RequestParam(defaultValue = "5") int k) {
        List<Map<String, String>> corpus = collectCorpus();
        return ResponseEntity.ok(nlpPipelineService.ldaTopics(corpus, k));
    }

    /**
     * GET /api/nlp/cluster-ideas?k=3
     * Combining topic modeling (D) with idea extraction (C): groups crawled posts into
     * K clusters via LDA, surfaces top words per cluster as topic summaries, and highlights
     * the most frequently occurring terms (with IDF boost) for each emerging theme.
     */
    @GetMapping("/cluster-ideas")
    public ResponseEntity<List<Map<String, Object>>> clusteredIdeas(
            @RequestParam(defaultValue = "3") int k) {
        List<Map<String, String>> corpus = collectCorpus();
        return ResponseEntity.ok(nlpPipelineService.clusteredIdas(corpus, k));
    }

    /**
     * GET /api/nlp/summary
     * Convenience endpoint that returns a high-level NLP summary: corpus stats,
     * top-10 TF-IDF keywords, and top-3 discovered topics (with 5 words each).
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> nlpSummary() {
        List<Map<String, String>> corpus = collectCorpus();

        Map<String, Object> summary = new LinkedHashMap<>();

        // Corpus statistics
        long totalDocs = corpus.stream().filter(d -> d.getOrDefault("title", "").length() > 0 ||
                d.getOrDefault("body", "").length() > 0).count();
        summary.put("corpusSize", (int) totalDocs);

        // Top TF-IDF keywords
        List<Map<String, Object>> topKeywords = nlpPipelineService.tfidfKeywords(corpus, 10);
        summary.put("topKeywords", topKeywords);

        // Top-3 LDA topics
        List<Map<String, Object>> topics = nlpPipelineService.ldaTopics(corpus, 3);
        summary.put("discoveredTopics", topics);

        return ResponseEntity.ok(summary);
    }
}
