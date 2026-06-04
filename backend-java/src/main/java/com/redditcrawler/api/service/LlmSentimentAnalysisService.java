package com.redditcrawler.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * LLM-powered sentiment analysis service.
 * Proxies text through an external LLM (OpenAI-compatible API) for nuanced sentiment detection
 * and returns structured results with confidence scores, emotion breakdowns, and language details.
 */
@Service
public class LlmSentimentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LlmSentimentAnalysisService.class);

    @Value("${llm.api.url:}")
    private String llmApiUrl;

    @Value("${llm.api.key:}")
    private String llmApiKey;

    @Value("${llm.model:gpt-4o-mini}")
    private String llmModel;

    /** Whether LLM is configured and available. */
    public boolean isAvailable() {
        return (llmApiUrl != null && !llmApiUrl.isEmpty())
            && (llmApiKey != null && !llmApiKey.isEmpty());
    }

    /**
     * Analyze sentiment of a single text via LLM API.
     */
    public Map<String, Object> analyze(String text) {
        if (!isAvailable()) {
            return Map.of(
                "error", "LLM sentiment analysis unavailable: missing llm.api.url or llm.api.key configuration",
                "available", false
            );
        }

        String prompt = buildPrompt(text, null);
        Map<String, Object> response = callLlm(prompt);

        if (response == null) {
            return Map.of(
                "sentiment", "unknown",
                "confidence", 0.0,
                "available", false
            );
        }

        // Extract structured fields from LLM JSON response
        String sentiment = extractString(response, "sentiment");
        Double confidence = extractDouble(response, "confidence");
        List<String> emotions = extractList(response, "emotions");
        Integer positiveTokens = extractInt(response, "positiveTokens");
        Integer negativeTokens = extractInt(response, "negativeTokens");
        String reason = extractString(response, "reason");

        return Map.of(
            "sentiment", sentiment != null ? sentiment : "unknown",
            "confidence", confidence != null ? confidence : 0.0,
            "emotions", emotions != null ? emotions : List.of(),
            "positiveTokens", positiveTokens != null ? positiveTokens : 0,
            "negativeTokens", negativeTokens != null ? negativeTokens : 0,
            "reason", reason != null ? reason : "N/A",
            "available", true
        );
    }

    /**
     * Batch analyze sentiment for multiple texts.
     */
    public List<Map<String, Object>> analyzeBatch(List<String> texts, int batchSize) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (!isAvailable()) {
            for (String t : texts) {
                results.add(Map.of("text", truncate(t, 200), "error", "LLM unavailable"));
            }
            return results;
        }

        for (int i = 0; i < texts.size() && i < batchSize; i++) {
            String text = texts.get(i);
            String prompt = buildPrompt(text, null);
            Map<String, Object> response = callLlm(prompt);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("text", truncate(text, 200));
            result.put("index", i);

            if (response != null) {
                String sentiment = extractString(response, "sentiment");
                Double confidence = extractDouble(response, "confidence");
                List<String> emotions = extractList(response, "emotions");
                String reason = extractString(response, "reason");

                result.put("sentiment", sentiment != null ? sentiment : "unknown");
                result.put("confidence", confidence != null ? confidence : 0.0);
                result.put("emotions", emotions != null ? emotions : List.of());
                result.put("reason", reason != null ? reason : "");
            } else {
                result.put("error", "LLM call failed");
                result.put("sentiment", "unknown");
                result.put("confidence", 0.0);
            }
            results.add(result);
        }
        return results;
    }

    /**
     * Analyze sentiment specifically for a subreddit context (summarizes multiple posts).
     */
    public Map<String, Object> analyzeSubredditSentiment(String subreddit, List<String> postContents) {
        if (!isAvailable()) {
            return new LinkedHashMap<>(Map.of(
                "subreddit", subreddit, "error", "LLM unavailable"
            ));
        }

        // Combine posts with indices into a single context for analysis
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < postContents.size() && i < 20; i++) {
            context.append("[Post ").append(i + 1).append("] ")
                   .append(postContents.get(i)).append("\n");
        }

        String prompt = buildPrompt(
            context.toString(),
            String.format("Analyze the overall sentiment of subreddit r/%s based on these recent posts.", subreddit)
        );

        Map<String, Object> response = callLlm(prompt);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subreddit", subreddit);
        result.put("postsAnalyzed", Math.min(postContents.size(), 20));

        if (response != null) {
            String overallSentiment = extractString(response, "sentiment");
            Double confidence = extractDouble(response, "confidence");
            List<String> keyEmotions = extractList(response, "emotions");
            String summary = extractString(response, "summary");
            String concernAreas = extractString(response, "concernAreas");

            result.put("overallSentiment", overallSentiment != null ? overallSentiment : "unknown");
            result.put("confidence", confidence != null ? confidence : 0.0);
            result.put("keyEmotions", keyEmotions != null ? keyEmotions : List.of());
            result.put("summary", summary != null ? summary : "");
            result.put("concernAreas", concernAreas != null ? concernAreas : "");
        } else {
            result.put("error", "LLM call failed");
        }

        return result;
    }

    /**
     * Return LLM configuration status for health checks.
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("available", isAvailable());
        if (isAvailable()) {
            status.put("apiUrl", llmApiUrl);
            status.put("model", llmModel);
        }
        return status;
    }

    // ==================== Private helpers ====================

    private String buildPrompt(String text, String context) {
        return """
            Analyze the sentiment of the following text. Respond with ONLY valid JSON (no markdown, no extra text):\
            {"sentiment":"positive|negative|neutral","confidence":0.xx,"emotions":["list"],"positiveTokens":N,"negativeTokens":N,"reason":"brief explanation"}\
            %s
            ---\
            Text: %s
            """.formatted(context != null ? "\n\nContext: " + context : "", text);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callLlm(String prompt) {
        if (!isAvailable()) return null;

        // Try multiple API endpoint patterns for compatibility
        String[] endpoints = {llmApiUrl};
        // Also try adding /v1/chat/completions if not already present
        if (!llmApiUrl.contains("chat/completions") && !llmApiUrl.endsWith("/")) {
            endpoints = new String[]{llmApiUrl + "/v1/chat/completions"};
        }

        log.debug("Attempting LLM sentiment analysis via: {}", endpoints[0]);

        for (String endpoint : endpoints) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(llmApiKey);

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", llmModel);
                body.put("temperature", 0.1);
                body.put("max_tokens", 500);
                body.put("response_format", Map.of("type", "json_object"));

                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", "You are a precise sentiment analysis assistant. Return only the JSON format specified."));
                messages.add(Map.of("role", "user", "content", prompt));

                body.put("messages", messages);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

                RestTemplate client = new RestTemplate();
                ResponseEntity<String> response = client.postForEntity(endpoint, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return parseLlmResponse(response.getBody());
                } else {
                    log.warn("LLM returned non-200: {} for endpoint {}", response.getStatusCode(), endpoint);
                }
            } catch (RestClientException e) {
                log.debug("LLM call failed on {}: {}", endpoint, e.getMessage());
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseLlmResponse(String body) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(body);

            // Extract from standard OpenAI response format
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).path("message").path("content").asText();
                if (content != null && !content.isEmpty()) {
                    // Strip markdown ```json wrapper if present
                    content = content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```\s*$", "");
                    return mapper.readValue(content, Map.class);
                }
            }

            // Fallback: try root directly as the JSON response
            if (root.isObject()) {
                return mapper.convertValue(root, Map.class);
            }
        } catch (Exception e) {
            log.warn("Failed to parse LLM response: {}", e.getMessage());
        }
        return null;
    }

    private String extractString(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key)).map(Object::toString).orElse(null);
    }

    private Double extractDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return null;
    }

    private Integer extractInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s != null ? s : "";
        return s.substring(0, Math.max(0, max - 3)) + "...";
    }
}
