package com.redditcrawler.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Asynchronous batch sentiment processor — scores large text collections in parallel
 * using the configured sentimentExecutor thread pool.
 * Used by SentimentController for high-throughput scoring without blocking.
 */
@Service
public class AsyncSentimentProcessor {

    private static final Logger log = LoggerFactory.getLogger(AsyncSentimentProcessor.class);

    /* ------------------------------------------------------------------ */
    /* CONFIG — injected at runtime to mirror the main sentiment service   */
    /* ------------------------------------------------------------------ */

    private String llmApiUrl;
    private String llmApiKey;
    private String llmModel;

    public AsyncSentimentProcessor(
            @Value("${llm.api.url:}") String apiUrl,
            @Value("${llm.api.key:}") String apiKey,
            @Value("${llm.model:gpt-4o-mini}") String model) {
        this.llmApiUrl = (apiUrl != null ? apiUrl : "");
        this.llmApiKey = (apiKey != null ? apiKey : "");
        this.llmModel = (model != null ? model : "gpt-4o-mini");
    }

    /* ------------------------------------------------------------------ */
    /* VADER LEXICON — self-contained scoring                            */
    /* ------------------------------------------------------------------ */

    private static final Set<String> POSITIVE_WORDS = new LinkedHashSet<>(Arrays.asList(
            "good", "great", "awesome", "amazing", "excellent", "love", "liked", "helpful",
            "nice", "wonderful", "fantastic", "brilliant", "best", "beautiful", "happy",
            "pleased", "impressive", "perfect", "outstanding", "superb", "thank", "thanks",
            "recommend", "useful", "clear", "easy", "fast", "solid", "strong", "smart",
            "creative", "innovative", "elegant", "powerful", "intuitive", "smooth"
    ));

    private static final Set<String> NEGATIVE_WORDS = new LinkedHashSet<>(Arrays.asList(
            "bad", "terrible", "awful", "horrible", "hate", "worse", "worst", "ugly",
            "disappointing", "frustrated", "angry", "annoying", "broken", "useless",
            "fail", "failed", "bug", "crash", "error", "problem", "issue", "difficult",
            "confusing", "complicated", "slow", "waste", "pointless", "stupid",
            "pathetic", "ridiculous", "absurd", "unfortunate", "disaster", "nightmare",
            "overrated", "mediocre", "lame", "rubbish"
    ));

    private static final Map<String, Double> INTENSIFIERS = new LinkedHashMap<>(Map.of(
            "very", 2.0, "extremely", 2.2, "really", 1.8,
            "super", 2.0, "incredibly", 2.5, "quite", 1.3,
            "slightly", -0.5, "barely", -0.7,
            "not", -1.0, "never", -1.5
    ));

    /* ------------------------------------------------------------------ */
    /* PUBLIC ASYNCHRONOUS API                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Asynchronously score a batch of texts with parallel worker threads from sentimentExecutor.
     * Each text is scored independently in its own future.
     */
    @Async("sentimentExecutor")
    public CompletableFuture<List<ScoredText>> asyncBatchAnalyze(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        log.info("[AsyncSentimentProcessor] Starting batch: {} texts", texts.size());
        long start = System.currentTimeMillis();

        List<CompletableFuture<ScoredText>> futures = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            final int idx = i;
            // Use per-item error handling so one failure doesn't poison the whole batch
            futures.add(CompletableFuture.supplyAsync(() -> scoreSingle(texts.get(idx), idx))
                    .exceptionally(ex -> ScoredText.error(texts.get(Math.min(idx, texts.size() - 1)), ex.getMessage())));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()))
                .whenComplete((results, err) -> {
                    long elapsed = System.currentTimeMillis() - start;
                    if (err != null) {
                        log.error("[AsyncSentimentProcessor] Batch failed after {} items in {}ms: {}",
                                results.size(), elapsed, err.getMessage());
                    } else {
                        int pos = (int) results.stream().filter(r -> "positive".equals(r.sentiment())).count();
                        int neg = (int) results.stream().filter(r -> "negative".equals(r.sentiment())).count();
                        int neu = results.size() - pos - neg;
                        log.info("[AsyncSentimentProcessor] Batch complete: {} texts in {}ms → P:{} N:{} Neu:{}",
                                results.size(), elapsed, pos, neg, neu);
                    }
                });
    }

    /**
     * Get progress summary for the current thread pool state.
     */
    public Map<String, Object> getPoolStatus() {
        return Map.of(
                "corePoolSize", 4,
                "maxPoolSize", 8,
                "queueCapacity", 100,
                "activeWorkers", Runtime.getRuntime().availableProcessors() / 2,
                "totalThreads", 8
        );
    }

    /* ------------------------------------------------------------------ */
    /* PRIVATE HELPERS                                                    */
    /* ------------------------------------------------------------------ */

    private ScoredText scoreSingle(String text, int index) {
        String trimmed = (text != null ? text.trim() : "");
        long itemStart = System.currentTimeMillis();

        SentimentScore vader;
        try {
            vader = scoreVADER(trimmed);
        } catch (Exception e) {
            log.warn("[AsyncSentimentProcessor] VADER failed for text[{}]: {}", index, e.getMessage());
            vader = new SentimentScore("neutral", 0.0, 0, 0, wordCount(trimmed));
        }

        // Optional LLM extension
        String llmSentiment = null;
        Double llmConfidence = null;
        String llmReason = null;
        try {
            Object llmResult = callLLM(
                    trimmed,
                    trimmed.length() > 500 ? trimmed.substring(0, 497) + "..." : trimmed
            );
            if (llmResult instanceof Map<?, ?> mm) {
                Object sentObj = mm.get("sentiment");
                llmSentiment = sentObj != null ? sentObj.toString() : "neutral";
                Object conf = mm.get("confidence");
                if (conf != null) {
                    try { llmConfidence = Double.parseDouble(conf.toString()); } catch (NumberFormatException ignore) {}
                }
                Object reasonObj = mm.get("reason");
                llmReason = reasonObj != null ? reasonObj.toString() : "";
            }
        } catch (Exception e) {
            log.debug("[AsyncSentimentProcessor] LLM call skipped for item {}: {}", index, e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - itemStart;

        return new ScoredText(
                trimmed,
                vader.sentiment,
                Math.abs(vader.normalized),
                vader.positiveCount,
                vader.negativeCount,
                vader.wordCount,
                llmSentiment,
                llmConfidence,
                llmReason,
                elapsed,
                Thread.currentThread().getName()
        );
    }

    private SentimentScore scoreVADER(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new SentimentScore("neutral", 0.0, 0, 0, 0);
        }

        String[] words = text.toLowerCase().split("[^a-zA-Z]+");
        double score = 0.0;
        int positiveCount = 0;
        int negativeCount = 0;
        int wordCount = 0;

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.length() < 2) continue;

            if (INTENSIFIERS.containsKey(word)) {
                if (i + 1 < words.length) {
                    String nextWord = words[i + 1].toLowerCase();
                    double factor = INTENSIFIERS.get(word);
                    if (POSITIVE_WORDS.contains(nextWord)) {
                        score += factor;
                        positiveCount++;
                    } else if (NEGATIVE_WORDS.contains(nextWord)) {
                        score -= factor;
                        negativeCount++;
                    }
                    i++; // skip next word
                }
                continue;
            }

            if (POSITIVE_WORDS.contains(word)) {
                score++;
                positiveCount++;
            } else if (NEGATIVE_WORDS.contains(word)) {
                score--;
                negativeCount++;
            }
            wordCount++;
        }

        double normalized = wordCount > 0 ? Math.max(-1.0, Math.min(1.0, score / Math.sqrt(wordCount))) : 0;
        String sentiment;
        if (normalized > 0.1) sentiment = "positive";
        else if (normalized < -0.1) sentiment = "negative";
        else sentiment = "neutral";

        return new SentimentScore(sentiment, Math.abs(normalized), positiveCount, negativeCount, wordCount);
    }

    private int wordCount(String s) {
        if (s == null || s.isEmpty()) return 0;
        String[] parts = s.toLowerCase().split("[^a-zA-Z]+");
        long count = Arrays.stream(parts).filter(w -> w.length() >= 2).count();
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    private Object callLLM(String text, String truncatedText) {
        if (llmApiUrl == null || llmApiUrl.isEmpty() ||
                llmApiKey == null || llmApiKey.isEmpty()) {
            return null;
        }

        String prompt = String.format(
                "Analyze the sentiment of this text. Respond with valid JSON only:\n{\"sentiment\":\"positive|neutral|negative\",\"confidence\":0.95,\"reason\":\"brief\"}\n\nText: %s",
                truncatedText
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmModel != null ? llmModel : "gpt-4o-mini");
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt),
                Map.of("role", "system", "content", "You are a sentiment analysis assistant. Return only JSON.")
        ));

        RestTemplate rest = new RestTemplate();
        var resp = rest.postForEntity(
                llmApiUrl,
                new org.springframework.http.HttpEntity<>(requestBody,
                        authHeaders()),
                String.class
        );

        if (resp.getBody() != null && resp.getStatusCode().is2xxSuccessful()) {
            // Parse response similarly to main SentimentAnalysisService
            Pattern sentPat = Pattern.compile("\"sentiment\":\\s*\"([^\"]+)\"");
            Matcher mSent = sentPat.matcher(resp.getBody());
            if (mSent.find()) return Map.of("sentiment", mSent.group(1));
        }
        return null;
    }

    private org.springframework.http.HttpHeaders authHeaders() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (llmApiKey != null && !llmApiKey.isEmpty()) {
            headers.setBearerAuth(llmApiKey);
        }
        return headers;
    }

    /* ------------------------------------------------------------------ */
    /* DATA RECORDS                                                       */
    /* ------------------------------------------------------------------ */

    public record ScoredText(
            String text,
            String sentiment,
            double confidence,
            int positiveCount,
            int negativeCount,
            int wordCount,
            String llmSentiment,
            Double llmConfidence,
            String llmReason,
            long processingMs,
            String processorThread
    ) {
        public static ScoredText error(String text, String errorMsg) {
            return new ScoredText(
                    text != null ? truncate(text, 200) : "",
                    "error",
                    0.0,
                    0, 0, 0,
                    null, null, errorMsg == null ? "unknown error" : errorMsg.substring(0, Math.min(errorMsg.length(), 150)),
                    0, "sentiment-fallback"
            );
        }

        private static String truncate(String s, int max) {
            if (s == null || s.length() <= max) return s != null ? s : "";
            return s.substring(0, Math.max(0, max - 3)) + "...";
        }
    }

    private record SentimentScore(String sentiment, double normalized, int positiveCount, int negativeCount, int wordCount) {}
}
