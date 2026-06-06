package com.redditcrawler.api.service;

import com.redditcrawler.api.dto.IdeaItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts structured ideas from raw Reddit thread text.
 *
 * Two modes of extraction:
 * 1. HEURISTIC (original): Uses keyword matching for title, description, category.
 *    - {@link #extractIdeas(String)} and {@link #extractFromThread(List)}
 *
 * 2. LLM-POWERED (new): Calls remote Ollama endpoint via RestClient to extract ideas
 *    with relevance scores, sentiment analysis, and keyword extraction.
 *    - {@link #extractIdeaList(String, Map, int)}
 */
@Service
public class IdeaExtractionService {

    private static final Logger log = LoggerFactory.getLogger(IdeaExtractionService.class);

    // Ollama remote endpoint configuration
    private static final String OLLAMA_BASE_URL = "http://192.168.100.1:11434/v1";

    // --------------- heuristic categories & keywords ----------------------------------

    private static final String CAT_GENERAL = "general";

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("tutorial", List.of(
                "how to", "steps", "guide", "tutorial", "learn", "beginner", "step 1", "first",
                "install", "setup", "configure", "introduction", "getting started", "newbie"
        ));
        CATEGORY_KEYWORDS.put("opinion", List.of(
                "i think", "in my opinion", "i feel", "personal take", "my experience",
                "personally", "from my view", "imo", "imho", "unpopular take", "hot take"
        ));
        CATEGORY_KEYWORDS.put("question", List.of(
                "why is", "how do i", "can someone", "what about", "anyone know",
                "does anyone", "i'm confused", "help me", "what should i"
        ));
        CATEGORY_KEYWORDS.put("news", List.of(
                "just announced", "breaking", "update", "now available", "released", "launched",
                "officially", "new version", "today", "recent"
        ));
        CATEGORY_KEYWORDS.put("discussion", List.of(
                "what do you think", "let's discuss", "debate", "pros and cons", "arguments for",
                "versus", "vs", "compare", "should we", "thoughts"
        ));
        CATEGORY_KEYWORDS.put("tech", List.of(
                "java", "python", "react", "spring", "kubernetes", "docker", "api", "database",
                "algorithm", "framework", "open source", "github", "maven", "gradle"
        ));
        CATEGORY_KEYWORDS.put("career", List.of(
                "job", "hiring", "interview", "salary", "remote work", "resume", "linkedin",
                "full stack", "senior developer", "engineering manager"
        ));
        CATEGORY_KEYWORDS.put("review", List.of(
                "review", "rating", "pros and cons", "worth it", "disappointing", "impressive",
                "recommend", "avoid", "verdict"
        ));
    }

    // --------------- idea model (heuristic) ---------------------------------------------

    public record Idea(String title, String description, String category, double confidence) {
        /** Resolve the string category to a stable enum. */
        public Category resolveCategory() {
            try {
                return Category.valueOf(category.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Category.GENERAL;
            }
        }

        public enum Category {
            TUTORIAL, OPINION, QUESTION, NEWS, DISCUSSION, TECH, CAREER, REVIEW, GENERAL
        }
    }

    // --------------- LLM REST client ----------------------------------------------

    private final RestClient restClient;

    public IdeaExtractionService() {
        this.restClient = RestClient.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // =========================================================================
    // LLM-POWERED EXTRACTION (NEW)
    // extractIdeaList(String query, Map<String,Object> modelConfig, int maxIdeas)
    // Returns: List<IdeaItem> with title, summary, relevanceScore, sentiment, relatedKeywords
    // =========================================================================

    /**
     * Extract a ranked list of ideas from raw text using the LLM.
     * Asks Ollama to return JSON matching {@link com.redditcrawler.api.dto.IdeaItem}.
     */
    public List<IdeaItem> extractIdeaList(String query, Map<String, Object> modelConfig, int maxIdeas) {

        String chosenModel = (String) modelConfig.getOrDefault("model", "qwen3.6:35b");
        Integer temperature = (Integer) modelConfig.getOrDefault("temperature", 0);
        if (temperature == null) temperature = 0;

        // Assemble system + user prompts for structured idea extraction
        String systemPrompt = """
                You are an expert content analyst specializing in extracting structured ideas from Reddit discussions.
                Your task is to identify distinct, actionable ideas mentioned across Reddit thread content.
                
                Respond ONLY with a valid JSON array of objects — no markdown fences, no preamble, no explanation.
                Each object MUST have these exact fields:
                - "title" (string): A concise descriptive title for the idea (max 60 characters).
                - "summary" (string): A brief summary of the idea (2-4 sentences max).
                - "relevanceScore" (number): A float between 0.0 and 1.0 indicating how relevant/valuable this idea is.
                - "sentiment" (string): Either "positive", "negative", or "neutral" — representing the overall tone of the discussion around this idea.
                - "relatedKeywords" (array of strings): 3-6 key phrases that summarize what this idea is about.
                
                Sort the final array by relevanceScore in descending order. Prioritize actionable ideas and insights over general observations.
                Include a diverse range of perspectives, not just popular opinions.
                Return EXACTLY maxIdeas entries (or fewer if context does not support more).
                Ensure the JSON is strictly valid — it will be parsed automatically.
                """;

        String userPrompt = query != null && !query.isBlank()
                ? query + "\n\n---\nNow extract ideas from the content above."
                : "Analyze and extract structured ideas from the available context.\n\n"
                    + "Please identify distinct, actionable insights with relevance scores and sentiment analysis.";

        // Build the OpenAI-compatible chat completion request
        Map<String, Object> messages = new LinkedHashMap<>();
        messages.put("role", "system");
        messages.put("content", systemPrompt);

        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);

        // Handle potential extra messages (e.g., history) from modelConfig
        @SuppressWarnings("unchecked")
        List<Object> existingMessages = (List<Object>) modelConfig.get("messages");
        if (existingMessages != null && !existingMessages.isEmpty()) {
            // Find the last user message index and insert our system prompt before it
            int insertIdx = existingMessages.size();
            for (int i = existingMessages.size() - 1; i >= 0; i--) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) existingMessages.get(i);
                if ("user".equals(m.get("role"))) {
                    insertIdx = i;
                    break;
                }
            }
            existingMessages.add(insertIdx, messages); // add userMsg after system
        } else {
            // Build conversation: system + user
            List<Object> conversation = new ArrayList<>();
            conversation.add(messages);

            Map<String, Object> uMsg = new LinkedHashMap<>();
            uMsg.put("role", "user");
            uMsg.put("content", userPrompt);
            conversation.add(uMsg);

            // Replace system as the first entry so we build correctly
            List<Object> finalMessages = new ArrayList<>();
            finalMessages.add(messages); // system
            Map<String, Object> finalUserMsg = new LinkedHashMap<>();
            finalUserMsg.put("role", "user");
            finalUserMsg.put("content", userPrompt);
            finalMessages.add(finalUserMsg);

            existingMessages = finalMessages;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> chatRequest = new LinkedHashMap<>();
        chatRequest.put("model", chosenModel);
        chatRequest.put("temperature", temperature);
        chatRequest.put("response_format", Map.of("type", "json_object"));
        chatRequest.put("messages", existingMessages);

        log.info("Calling Ollama LLM (model={}, maxIdeas={}) for idea extraction", chosenModel, maxIdeas);

        ResponseEntity<Map> responseEntity;
        try {
            responseEntity = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(chatRequest)
                    .retrieve()
                    .toEntity(Map.class);
        } catch (Exception e) {
            log.error("Failed to call Ollama endpoint {}: {}", OLLAMA_BASE_URL, e.getMessage());
            throw new RuntimeException("LLM idea extraction failed: " + e.getMessage(), e);
        }

        if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
            Map<String, Object> body = responseEntity.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                @SuppressWarnings("unchecked")
                Map<String, String> message = (Map<String, String>) choice.get("message");
                if (message != null && message.containsKey("content")) {
                    String content = message.get("content");
                    log.info("LLM returned {} characters of idea extraction response", content.length());

                    // Parse the JSON array from the LLM's response
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        List<IdeaItem> ideas = objectMapper.readValue(
                                cleanJsonResponse(content),
                                new com.fasterxml.jackson.core.type.TypeReference<List<IdeaItem>>() {});

                        // Sort by relevance descending
                        ideas.sort(Comparator.comparingDouble(IdeaItem::getRelevanceScore).reversed());

                        // Enforce maxIdeas limit
                        if (ideas.size() > maxIdeas) {
                            ideas = ideas.subList(0, maxIdeas);
                        }

                        log.info("Successfully extracted {} ideas via LLM", ideas.size());
                        return ideas;
                    } catch (Exception parseEx) {
                        log.error("Failed to parse LLM response as JSON: {}", parseEx.getMessage());
                        // Fallback: try heuristic parsing of string output
                        return fallbackParseIdeas(content, maxIdeas);
                    }
                }
            }
        }

        log.warn("No ideas extracted — unexpected LLM response or empty choices");
        throw new RuntimeException("Idea extraction returned no results from LLM");
    }

    /** Strip markdown code fences and surrounding whitespace from LLM output. */
    private String cleanJsonResponse(String content) {
        if (content == null) return "[]";
        String stripped = content.trim();
        if (stripped.startsWith("```")) {
            // Remove ```json ... ``` or ``` ... ```
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline > 0 && firstNewline + 1 < stripped.length()) {
                String afterFence = stripped.substring(firstNewline + 1);
                stripped = afterFence;
            }
            // Remove trailing ```
            int lastBacktick = stripped.lastIndexOf('`');
            if (lastBacktick > 0) {
                stripped = stripped.substring(0, lastBacktick).trim();
            }
        }
        return stripped;
    }

    /** Heuristic fallback when LLM JSON parsing fails. */
    private List<IdeaItem> fallbackParseIdeas(String content, int maxIdeas) {
        log.info("Falling back to heuristic parsing of raw text ({} chars)", content.length());
        List<String> sentences = splitSentences(normalize(content));

        Set<String> seenTitles = new LinkedHashSet<>();
        List<IdeaItem> ideas = new ArrayList<>();

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() < 30 || trimmed.length() > 500) continue;
            if (!seenTitles.add(truncate(trimmed, 60))) continue;

            IdeaItem item = new IdeaItem(
                    capitalize(truncate(trimmed, 60)),
                    trimmed,
                    Math.round((0.4 + Math.random() * 0.5) * 100.0) / 100.0,
                    classifySentiment(trimmed),
                    extractKeywords(trimmed)
            );
            ideas.add(item);
            if (ideas.size() >= maxIdeas) break;
        }

        return ideas;
    }

    /** Classify sentiment using simple keyword heuristics. */
    private String classifySentiment(String text) {
        String lower = text.toLowerCase();
        int positive = 0, negative = 0;
        // Positive words
        for (String w : List.of("good", "great", "excellent", "love", "impressive", "amazing", "perfect", "best", "recommend", "helpful")) {
            if (lower.contains(w)) positive++;
        }
        // Negative words
        for (String w : List.of("bad", "terrible", "awful", "hate", "disappointing", "worst", "avoid", "useless", "waste", "fail")) {
            if (lower.contains(w)) negative++;
        }
        if (positive > negative) return "positive";
        if (negative > positive) return "negative";
        return "neutral";
    }

    /** Extract candidate keywords from text by splitting and filtering. */
    private List<String> extractKeywords(String text) {
        String lower = text.toLowerCase();
        Set<String> stopWords = Set.of("the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did", "will", "would", "could", "should", "may",
                "can", "shall", "to", "of", "in", "for", "on", "with", "at", "by", "from", "as",
                "into", "through", "during", "before", "after", "and", "but", "or", "nor", "not");

        String[] tokenArray = text.replaceAll("[^a-zA-Z0-9\\s]", " ").split("\\s+");
        List<String> tokens = Arrays.stream(tokenArray).toList();
        Set<String> result = new LinkedHashSet<>();
        for (String t : tokens) {
            String low = t.toLowerCase();
            if (low.length() >= 4 && !stopWords.contains(low)) {
                result.add(t);
                if (result.size() >= 6) break;
            }
        }

        List<String> keywordList = new ArrayList<>(result);
        return keywordList.isEmpty() ? List.of("general") : keywordList;
    }

    // =========================================================================
    // HEURISTIC EXTRACTION (original, retained for backward compatibility)
    // =========================================================================

    /** Extract structured ideas from raw text using keyword-based heuristics. */
    public List<Idea> extractIdeas(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            log.warn("extractIdeas called with blank input");
            return Collections.emptyList();
        }

        String cleaned = normalize(rawText);
        List<String> sentences = splitSentences(cleaned);

        if (sentences.isEmpty()) {
            log.info("No extractable sentences found");
            return Collections.emptyList();
        }

        Set<String> seenTitles = new LinkedHashSet<>();
        List<Idea> ideas = new ArrayList<>();

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() < 20) continue;

            String title = truncate(trimmed, 80);
            if (!seenTitles.add(title)) continue; // dedup candidate

            Idea idea = classifyAndBuild(trimmed, title);
            ideas.add(idea);
        }

        log.info("extracted {} ideas from input ({} chars)", ideas.size(), cleaned.length());
        return ideas;
    }

    /** Extract ideas from a list of Reddit post bodies. */
    public List<Idea> extractFromThread(List<String> postBodies) {
        if (postBodies == null || postBodies.isEmpty()) {
            return Collections.emptyList();
        }
        String combined = String.join("\n\n", postBodies);
        List<Idea> allIdeas = extractIdeas(combined);
        return deduplicateByTitle(allIdeas);
    }

    // =========================================================================
    // Classification & helpers (heuristic)
    // =========================================================================

    private Idea classifyAndBuild(String text, String title) {
        String lower = text.toLowerCase();
        String bestCategory = CAT_GENERAL;
        double bestScore = 0.0;

        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            double score = 0.0;
            for (String kw : entry.getValue()) {
                if (lower.contains(kw.toLowerCase())) {
                    // Shorter keyword matches are more specific -> higher weight
                    score += Math.max(0.15, 1.0 - kw.length() * 0.02);
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestCategory = entry.getKey();
            }
        }

        // Boost confidence for tech/career specific matches
        double confidence = Math.min(0.95, 0.4 + bestScore * 0.1);
        if (bestCategory.equals("tech") || bestCategory.equals("career")) {
            confidence = Math.min(0.98, confidence + 0.2);
        }

        return new Idea(title, text, bestCategory, round(confidence, 2));
    }

    private static String normalize(String text) {
        return text.replaceAll("\n\\s*\n", "\n")
                   .replaceAll("[\\t ]{3,}", " ")
                   .replaceAll("\\r", "");
    }

    private static List<String> splitSentences(String text) {
        Pattern p = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z\\\"'«])|(?<=[.!?])\\s*$");
        String[] parts = p.split(text);
        List<String> results = new ArrayList<>();

        for (String part : parts) {
            String trimmed = part.trim().replaceAll("^[-•*>]+\\s*", "");
            if (trimmed.length() >= 20 && trimmed.length() <= 500) {
                if (!trimmed.startsWith("```") && !trimmed.isEmpty()) {
                    results.add(trimmed);
                }
            }
        }

        if (results.isEmpty()) {
            String[] byLine = text.split("(?<=\\n)");
            for (String line : byLine) {
                String t = line.trim();
                if (!t.isEmpty() && !t.matches("^[-*_#`>]+$")) {
                    results.add(t);
                }
            }
        }

        return results;
    }

    static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return capitalize(s);
        String truncated = s.substring(0, maxLen - 2) + "..";
        return truncateToWord(truncated);
    }

    private static String truncateToWord(String s) {
        int lastSpace = s.lastIndexOf(' ');
        if (lastSpace > 0) return s.substring(0, lastSpace + 1).trim() + "...";
        return s;
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        char first = Character.toUpperCase(s.charAt(0));
        return first + s.substring(1);
    }

    private List<Idea> deduplicateByTitle(List<Idea> ideas) {
        Set<String> seen = new LinkedHashSet<>();
        return ideas.stream()
                .filter(i -> seen.add(i.title()))
                .collect(Collectors.toList());
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
