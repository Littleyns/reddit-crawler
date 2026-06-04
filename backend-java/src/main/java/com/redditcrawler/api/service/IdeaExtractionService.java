package com.redditcrawler.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts structured ideas from raw Reddit thread text.
 * Each idea: {title, description, category, confidence}.
 * Uses heuristic pattern matching + keyword lexicons for categorization.
 */
@Service
public class IdeaExtractionService {

    private static final Logger log = LoggerFactory.getLogger(IdeaExtractionService.class);
    private static final String CAT_GENERAL = "general";

    // ---- categories & keywords --------------------------------------------------

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

    // ---- idea model -------------------------------------------------------------

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

    // ---- extraction engine ------------------------------------------------------

    /**
     * Extract ideas from a single text body.
     */
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

    /**
     * Extract ideas from a collection of related posts (e.g. comments in a thread).
     */
    public List<Idea> extractFromThread(List<String> postBodies) {
        if (postBodies == null || postBodies.isEmpty()) {
            return Collections.emptyList();
        }
        String combined = String.join("\n\n", postBodies);
        List<Idea> allIdeas = extractIdeas(combined);
        return deduplicateByTitle(allIdeas);
    }

    // ---- classification ---------------------------------------------------------

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

    // ---- helpers ----------------------------------------------------------------

    private static String normalize(String text) {
        return text.replaceAll("\\n\\s*\\n", "\\n")
                   .replaceAll("[\\t ]{3,}", " ")
                   .replaceAll("\\r", "");
    }

    private static List<String> splitSentences(String text) {
        Pattern p = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z\"'«])|(?<=[.!?])\\s*$");
        String[] parts = p.split(text);
        List<String> results = new ArrayList<>();

        for (String part : parts) {
            String trimmed = part.trim().replaceAll("^[\\-•*>]+\\s*", "");
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

    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return capitalize(s);
        String truncated = s.substring(0, maxLen - 2) + "..";
        return truncateToWord(truncated);
    }

    private static String truncateToWord(String s) {
        int lastSpace = s.lastIndexOf(' ');
        if (lastSpace > 0) return s.substring(0, lastSpace + 1).trim() + "...";
        return s;
    }

    private static String capitalize(String s) {
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
