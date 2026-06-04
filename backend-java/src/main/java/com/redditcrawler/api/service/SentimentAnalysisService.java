package com.redditcrawler.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dedicated sentiment analysis service providing VADER-style lexicon scoring
 * and optional LLM-powered analysis for crawled Reddit posts.
 */
@Service
public class SentimentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(SentimentAnalysisService.class);

    /* ------------------------------------------------------------------ */
    /* CONFIGURATION                                                       */
    /* ------------------------------------------------------------------ */

    @Value("${llm.api.url:}")
    private String llmApiUrl;

    @Value("${llm.api.key:}")
    private String llmApiKey;

    @Value("${llm.model:gpt-4o-mini}")
    private String llmModel;

    public SentimentAnalysisService() {
        // default constructor for Spring
    }

    /* ------------------------------------------------------------------ */
    /* VADER LEXICON                                                       */
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
    /* PUBLIC API                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Analyze sentiment for a single piece of text using VADER-style lexicon.
     * Returns structured sentiment score with positive/negative/neutral counts and confidence.
     */
    public SentimentScore analyze(String text) {
        return scoreSentiment(text);
    }

    /**
     * Analyze sentiment for multiple texts, returning a list of scored entries.
     */
    public List<SentimentResult> analyzeBatch(List<String> texts) {
        return texts.stream()
                .map(t -> new SentimentResult(t, scoreSentiment(t)))
                .collect(Collectors.toList());
    }

    /**
     * Attempt LLM-powered sentiment analysis via external API.
     * Returns null if no LLM config is available or the call fails.
     */
    public Map<String, Object> analyzeViaLLM(String text) {
        if (llmApiUrl == null || llmApiUrl.isEmpty() || llmApiKey == null || llmApiKey.isEmpty()) {
            log.debug("LLM sentiment request skipped: no API configured");
            return null;
        }

        String prompt = String.format(
                "Analyze the sentiment of this text. Respond with valid JSON only:\n{\"sentiment\":\"positive|neutral|negative\",\"confidence\":0.95,\"reason\":\"brief\"}\n\nText: %s",
                truncate(text, 2000)
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmModel);
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt),
                Map.of("role", "system", "content", "You are a sentiment analysis assistant. Return only JSON.")
        ));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(llmApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = new RestTemplate()
                    .exchange(llmApiUrl, HttpMethod.POST, entity, String.class);

            if (response.getBody() != null && response.getStatusCode().is2xxSuccessful()) {
                // Parse the LLM's JSON response
                String body = response.getBody();
                String sentiment = "neutral";
                Double confidence = 0.5;
                String reason = "";

                // Extract sentiment
                Pattern sentPattern = Pattern.compile("\"sentiment\":\\s*\"([^\"]+)\"");
                Matcher mSent = sentPattern.matcher(body);
                if (mSent.find()) sentiment = mSent.group(1);

                // Extract confidence
                Pattern confPattern = Pattern.compile("\"confidence\":\\s*(\\d+\\.?\\d*)");
                Matcher mConf = confPattern.matcher(body);
                if (mConf.find()) confidence = Double.parseDouble(mConf.group(1));

                // Extract reason
                Pattern reasonPattern = Pattern.compile("\"reason\":\\s*\"([^\"]+)\"");
                Matcher mReason = reasonPattern.matcher(body);
                if (mReason.find()) reason = mReason.group(1);

                return Map.of(
                        "sentiment", sentiment,
                        "confidence", confidence,
                        "reason", reason,
                        "provider", "llm"
                );
            }
        } catch (Exception e) {
            log.warn("LLM sentiment analysis failed: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Combined analysis: returns both VADER and LLM results when available.
     */
    public CombinedAnalysis combined(String text) {
        SentimentScore vader = scoreSentiment(text);
        Map<String, Object> llmResult = analyzeViaLLM(text);

        return new CombinedAnalysis(vader, llmResult != null ? Map.copyOf(llmResult) : null);
    }

    /**
     * Sentiment summary across multiple texts.
     */
    public List<Map<String, Object>> summarize(List<String> texts) {
        long positiveCount = 0, negativeCount = 0, neutralCount = 0;
        List<SentimentResult> results = analyzeBatch(texts);

        for (SentimentResult r : results) {
            switch (r.score.sentiment()) {
                case "positive" -> positiveCount++;
                case "negative" -> negativeCount++;
                default -> neutralCount++;
            }
        }

        int total = texts.size();
        List<Map<String, Object>> summary = new ArrayList<>();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("positive", positiveCount);
        overview.put("negative", negativeCount);
        overview.put("neutral", neutralCount);
        overview.put("total", total);
        if (total > 0) {
            overview.put("positivePercent", Math.round((double) positiveCount / total * 1000.0) / 10.0);
            overview.put("negativePercent", Math.round((double) negativeCount / total * 1000.0) / 10.0);
            overview.put("overallBalance", (positiveCount - negativeCount));
        } else {
            overview.put("positivePercent", 0.0);
            overview.put("negativePercent", 0.0);
            overview.put("overallBalance", 0);
        }
        summary.add(overview);

        // Individual results capped at top 50 to avoid massive payloads
        summary.addAll(results.stream().limit(50).map(r -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("text", truncate(r.text(), 200));
            entry.put("sentiment", r.score().sentiment());
            entry.put("confidence", r.score().confidence);
            entry.put("positiveCount", r.score().positiveCount);
            entry.put("negativeCount", r.score().negativeCount);
            return entry;
        }).collect(Collectors.toList()));

        summary.add(Map.of("individualResultsCount", Math.min(results.size(), 50)));

        return summary;
    }

    /* ------------------------------------------------------------------ */
    /* PRIVATE HELPERS                                                     */
    /* ------------------------------------------------------------------ */

    private SentimentScore scoreSentiment(String text) {
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
                    i++; // skip the next word
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

        // Normalize score to [-1, 1] range based on word count
        double normalized = wordCount > 0 ? Math.max(-1.0, Math.min(1.0, score / Math.sqrt(wordCount))) : 0;
        String sentiment;
        if (normalized > 0.1) sentiment = "positive";
        else if (normalized < -0.1) sentiment = "negative";
        else sentiment = "neutral";

        return new SentimentScore(sentiment, Math.abs(normalized), positiveCount, negativeCount, wordCount);
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s != null ? s : "";
        return s.substring(0, Math.max(0, max - 3)) + "...";
    }

    /* ------------------------------------------------------------------ */
    /* DATA CLASSES                                                        */
    /* ------------------------------------------------------------------ */

    public record SentimentScore(String sentiment, double confidence, int positiveCount, int negativeCount, int wordCount) {}

    public record SentimentResult(String text, SentimentScore score) {}

    public record CombinedAnalysis(SentimentScore vader, Map<String, Object> llmResult) {}
}
