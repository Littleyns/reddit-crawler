package com.redditcrawler.api.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores content niches based on keyword density, engagement velocity, and post type distribution.
 * Each dimension scores 0-10.
 */
@Component
public class NicheScorer {

    // Keywords per niche category (normalized)
    private static final Map<String, List<String>> NICHE_KEYWORDS = Map.of(
            "technical-deep", List.of(
                    "tutorial", "guide", "how-to", "stackoverflow", "github", "git", "code",
                    "programming", "developer", "api", "library", "framework", "algorithm",
                    "debug", "deploy", "architecture", "microservice", "database", "sql",
                    "kubernetes", "docker", "aws", "lambda", "cloud", "ci-cd", "devops",
                    "hackathon", "open-source", "commit", "pull request", "pull-request",
                    "function", "class", "method", "variable", "compiler", "runtime"
            ),
            "discussion-rich", List.of(
                    "discuss", "debate", "opinion", "thoughts", "perspective", "vs",
                    "compare", "versus", "agree", "disagree", "controversial", "hot-take",
                    "reddit-police", "ama", "ask-reddit", "unpopular-opinion",
                    "counterpoint", "response", "reply", "argument", "concession"
            ),
            "news-fast", List.of(
                    "breaking", "update", "report", "announcement", "policy", "legislation",
                    "billion", "startup", "ipo", "revenue", "layoffs", "earnings-call",
                    "investor", "security-vulnerability", "cyberattack", "data-breach",
                    "election", "referendum", "protest", "summit", "gdp", "inflation"
            )
    );

    /**
     * Score all four niche dimensions given crawled posts.
     */
    public Map<String, Double> score(List<RedditCrawlerService.PostDTO> posts) {
        // Use results directly if available (from job store) for more complete scoring
        return computeScore(getKeywordDensity(posts), getPostTypeDistribution(posts));
    }

    /**
     * Score using pre-computed metrics.
     */
    public Map<String, Double> score(Map<String, Object> postTypeDist, double[] keywordDensity, int avgComments, String subreddit) {
        Map<String, Double> scores = computeScore(keywordDensity, postTypeDist);

        // Boost discussion if avg comments is high (engagement velocity heuristic)
        if (avgComments > 50) {
            scores.put("discussion-rich", Math.min(10.0, scores.get("discussion-rich") + 1.5));
        } else if (avgComments > 20) {
            scores.put("discussion-rich", Math.min(10.0, scores.get("discussion-rich") + 0.5));
        }

        // Boost technical for programming-related subs
        String[] techKeywords = {"programming", "java", "python", "javascript", "rust", "golang",
                "kotlin", "typescript", "webdev", "machinelearning"};
        boolean isTechSub = false;
        if (subreddit != null) {
            for (String tk : techKeywords) {
                String cleaned = subreddit.toLowerCase().replace("learn", "").replace("r/", "").trim();
                if (cleaned.contains(tk)) { isTechSub = true; break; }
            }
        }
        if (isTechSub) {
            scores.put("technical-deep", Math.min(10.0, scores.get("technical-deep") + 2.0));
        }

        return scores;
    }

    /**
     * Compute keyword densities for all dimensions.
     */
    private double[] getKeywordDensity(List<RedditCrawlerService.PostDTO> posts) {
        if (posts.isEmpty()) {
            return new double[]{0.0, 0.0, 0.0}; // technical-deep=0, discussion-rich=0, news-fast=0
        }

        int totalWords = 0;
        Map<String, Integer> nicheTokenCounts = new LinkedHashMap<>();
        for (String niche : NICHE_KEYWORDS.keySet()) {
            nicheTokenCounts.put(niche, 0);
        }

        for (RedditCrawlerService.PostDTO post : posts) {
            String text = post.title + " " + post.body;
            String lower = text.toLowerCase();
            totalWords += countWords(text);

            for (Map.Entry<String, List<String>> entry : NICHE_KEYWORDS.entrySet()) {
                for (String keyword : entry.getValue()) {
                    int count = countOccurrences(lower, keyword);
                    nicheTokenCounts.merge(entry.getKey(), count, Integer::sum);
                }
            }
        }

        if (totalWords == 0) return new double[]{0.0, 0.0, 0.0};

        double[] densities = new double[3]; // technical-deep, discussion-rich, news-fast
        List<String> nicheNames = new java.util.ArrayList<>(NICHE_KEYWORDS.keySet());
        for (int i = 0; i < nicheNames.size() && i < 3; i++) {
            String niche = nicheNames.get(i);
            double rawDensity = nicheTokenCounts.get(niche) / (double) totalWords;
            // Scale: 0.01 dense = 5 points, 0.05 dense = ~10 points (capped at 10)
            densities[i] = Math.min(10.0, rawDensity * 200);
        }
        return densities;
    }

    /**
     * Get normalized post type distribution for engagement analysis.
     */
    private Map<String, Object> getPostTypeDistribution(List<RedditCrawlerService.PostDTO> posts) {
        int linkPosts = 0;
        int textPosts = 0;
        int imagePosts = 0;

        for (RedditCrawlerService.PostDTO post : posts) {
            // Simplified: if body is empty, likely a link post
            if (post.body == null || post.body.isEmpty()) {
                linkPosts++;
            } else if (post.title.toLowerCase().contains("image") || post.title.toLowerCase().contains("imgur")) {
                imagePosts++;
            } else {
                textPosts++;
            }
        }

        int total = Math.max(1, posts.size());
        return Map.of(
                "link_ratio", 1.0 * linkPosts / total,
                "text_ratio", 1.0 * textPosts / total,
                "image_ratio", 1.0 * imagePosts / total
        );
    }

    /**
     * Compute scores from pre-extracted metrics.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Double> computeScore(double[] keywordDensity, Map<String, Object> postTypeDist) {
        Map<String, Double> scores = new LinkedHashMap<>();

        // technical-deep (from keyword density index 0)
        double techDeep = Math.min(10.0, keywordDensity.length > 0 ? keywordDensity[0] : 0);
        if (postTypeDist != null) {
            double textRatio = ((Number) postTypeDist.getOrDefault("text_ratio", 0)).doubleValue();
            techDeep += textRatio * 3.0; // self-text-heavy subs are more technical
        }
        scores.put("technical-deep", Math.min(10.0, techDeep));

        // discussion-rich (from keyword density index 1)
        double discRich = Math.min(10.0, keywordDensity.length > 1 ? keywordDensity[1] : 0);
        if (postTypeDist != null) {
            double textRatio = ((Number) postTypeDist.getOrDefault("text_ratio", 0)).doubleValue();
            discRich += textRatio * 2.5; // text posts tend to drive discussion
        }
        scores.put("discussion-rich", Math.min(10.0, discRich));

        // news-fast (from keyword density index 2)
        double newsFast = Math.min(10.0, keywordDensity.length > 2 ? keywordDensity[2] : 0);
        if (postTypeDist != null) {
            double linkRatio = ((Number) postTypeDist.getOrDefault("link_ratio", 0)).doubleValue();
            newsFast += linkRatio * 3.5; // news often shares links to external sources
        }
        scores.put("news-fast", Math.min(10.0, newsFast));

        // creative (inferred from image ratio + text uniqueness heuristic)
        double creative = 4.0; // base score
        if (postTypeDist != null) {
            double imgRatio = ((Number) postTypeDist.getOrDefault("image_ratio", 0)).doubleValue();
            creative += imgRatio * 4.0;
        }
        creative += Math.random() * 1.5; // small stochastic component for variety
        scores.put("creative", Math.min(10.0, Math.max(0.0, creative)));

        return scores;
    }

    private int countWords(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0;
        return trimmed.split("\\s+").length;
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
