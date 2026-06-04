package com.redditcrawler.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * NLP & data science analytics service backed by RedditCrawlerService.
 * Provides sentiment analysis, idea extraction, subreddit trends, keyword
 * frequency, and sentiment heatmaps -- all using pure-Java logic with an
 * optional LLM API extension for idea extraction.
 */
@Service
public class TextAnalysisService {

    /* ------------------------------------------------------------------ */
    /* SPRING CONFIGURATION                                               */
    /* ------------------------------------------------------------------ */

    @Value("${llm.api.url:}")
    private String llmApiUrl;

    @Value("${llm.api.key:}")
    private String llmApiKey;

    @Value("${llm.model:gpt-4o-mini}")
    private String llmModel;

    /* ------------------------------------------------------------------ */
    /* DEPENDENCY INJECTION                                               */
    /* ------------------------------------------------------------------ */

    private final RedditCrawlerService crawlerService;
    private final NicheScorer nicheScorer;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public TextAnalysisService(RedditCrawlerService crawlerService, NicheScorer nicheScorer) {
        this.crawlerService = crawlerService;
        this.nicheScorer = nicheScorer;
    }

    /* ------------------------------------------------------------------ */
    /* HELPERS                                                            */
    /* ------------------------------------------------------------------ */

    /** Collects every PostDTO from all crawled jobs. Falls back to seed data if empty. */
    @SuppressWarnings("unchecked")
    private List<RedditCrawlerService.PostDTO> getAllPosts() {
        List<RedditCrawlerService.PostDTO> posts = new ArrayList<>();
        for (Map<String, Object> job : crawlerService.getAllJobs()) {
            List<RedditCrawlerService.PostDTO> resultList = RedditCrawlerService.PostDTO.fromResults(
                    (List<Map<String, Object>>) job.get("resultsJson")
            );
            for (RedditCrawlerService.PostDTO p : resultList) {
                posts.add(p);
            }
        }
        return posts.isEmpty() ? getSeedPosts() : posts;
    }

    /** Seed synthetic posts for analytics visualization when no crawl data exists. */
    private List<RedditCrawlerService.PostDTO> getSeedPosts() {
        String[][] SEED_DATA = {
            {"r/MachineLearning", "Just found an amazing paper on transformer architectures! The attention mechanism is brilliant.", "positive"},
            {"r/MachineLearning", "Is there anyone who can help with a deep learning project?", "neutral"},
            {"r/MachineLearning", "Terrible experience with TensorFlow 2.x — constant crashes. Anyone else?", "negative"},
            {"r/MachineLearning", "Building an NLP pipeline for sentiment classification using GPT fine-tuning.", "positive"},
            {"r/MachineLearning", "Looking for a developer to collaborate on an AI chatbot project.", "neutral"},
            {"r/webdev", "React + Next.js 16 is fantastic for building modern web apps!", "positive"},
            {"r/webdev", "Need someone who can build a full-stack app with PostgreSQL and Docker.", "neutral"},
            {"r/webdev", "Worst debugging experience ever — CSS flexbox is broken in Safari again.", "negative"},
            {"r/webdev", "Just launched my SaaS on AWS. Best infrastructure decision I've made.", "positive"},
            {"r/webdev", "Want to build a web-based code editor with real-time collaboration.", "neutral"},
            {"r/MobileAppDevelopment", "Flutter 3 is great for cross-platform mobile development.", "positive"},
            {"r/MobileAppDevelopment", "Looking for someone to help build an iOS app with Swift UI.", "neutral"},
            {"r/MobileAppDevelopment", "Android Studio crashes every time I try to profile memory leaks.", "negative"},
            {"r/devops", "Docker Compose multi-service deployment saved us so much time.", "positive"},
            {"r/devops", "Just set up a Kubernetes cluster for our microservices — beautiful architecture.", "positive"},
            {"r/devops", "Hiring a DevOps engineer skilled in Terraform and CI/CD pipelines.", "neutral"},
            {"r/gamedev", "Working on a Godot 3D game prototype — the visual scripting is so intuitive!", "positive"},
            {"r/gamedev", "Anyone know a good library for procedural terrain generation?", "neutral"},
            {"r/gamedev", "My Unity project keeps crashing randomly. This is frustrating.", "negative"},
            {"r/datascience", "Using R and Shiny to build interactive dashboards — very powerful.", "positive"},
            {"r/datascience", "Looking for collaborators on a data journalism project using Python.", "neutral"},
            {"r/data_science", "Pandas is great but the performance issues with large datasets are awful.", "negative"},
            {"r/data_science", "Recommend a good course for learning SQL and data modeling?", "neutral"},
        };
        List<RedditCrawlerService.PostDTO> seed = new ArrayList<>();
        for (int i = 0; i < SEED_DATA.length; i++) {
            RedditCrawlerService.PostDTO dto = new RedditCrawlerService.PostDTO();
            dto.subreddit = SEED_DATA[i][0];
            dto.title = "Seed post #" + (i + 1) + ": Example topic";
            dto.body = SEED_DATA[i][1] + " Some additional context to make this more realistic for analytics. This post was generated as seed data";
            dto.upvotes = 10 + (i * 7 % 200);
            dto.commentsCount = 5 + (i * 3 % 80);
            dto.permalink = "/r/" + SEED_DATA[i][2] + "/seed/" + i;
            seed.add(dto);
        }
        return seed;
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s != null ? s : "";
        return s.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static final int MAX_SENTIMENT_TEXT = 200;

    /* ------------------------------------------------------------------ */
    /* PUBLIC API                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * 1) analyzeSentimentBySubreddit
     * Scans all crawled posts, scores each with a VADER-style weighted lexicon.
     * Returns List&lt;Map&gt; ready for JSON serialization.
     */
    public List<Map<String, Object>> analyzeSentimentBySubreddit() {
        List<RedditCrawlerService.PostDTO> posts = getAllPosts();
        List<Map<String, Object>> results = new ArrayList<>();

        for (RedditCrawlerService.PostDTO post : posts) {
            String text = buildFullText(post);
            SentimentScore score = scoreSentiment(text.trim());

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("url", post.permalink != null ? post.permalink : "");
            entry.put("text", truncate(text, MAX_SENTIMENT_TEXT));
            entry.put("sentiment", score.sentiment);
            entry.put("confidence", Math.round(score.confidence * 100.0) / 100.0);
            entry.put("positiveCount", score.positiveCount);
            entry.put("negativeCount", score.negativeCount);
            entry.put("wordCount", score.wordCount);

            results.add(entry);
        }

        return results;
    }

    /**
     * 2) extractIdeas(String category)
     * Returns actionable project ideas with title, description, category.
     * Uses LLM when configured, falls back to regex-pattern matching.
     * Optional category filter (null = all categories).
     */
    public List<Map<String, Object>> extractIdeas(String category) {
        List<RawIdea> rawIdeas = new ArrayList<>();

        // Try LLM extraction first if API is configured
        if (llmApiUrl != null && !"".equals(llmApiUrl) && llmApiKey != null && !"".equals(llmApiKey)) {
            rawIdeas.addAll(extractIdeasViaLLM());
        }

        // Always add heuristic matches
        rawIdeas.addAll(extractIdeasHeuristic());

        // Deduplicate by similar title
        List<RawIdea> unique = new ArrayList<>();
        for (RawIdea idea : rawIdeas) {
            boolean dup = false;
            for (RawIdea existing : unique) {
                if (existing.title != null && idea.title != null) {
                    String eTitle = existing.title.toLowerCase().trim();
                    String iTle  = idea.title.toLowerCase().trim();
                    if (eTitle.contains(iTle) || iTle.contains(eTitle)) {
                        dup = true;
                        break;
                    }
                }
            }
            if (!dup) {
                unique.add(idea);
            }
        }

        List<Map<String, Object>> ideas = new ArrayList<>();
        for (RawIdea idea : unique) {
            // Apply category filter if requested
            if (category != null && !category.equals("all")) {
                String cat = idea.category != null ? idea.category : "other";
                if (!cat.equals(category)) continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", idea.title != null ? idea.title : "");
            m.put("description", idea.description != null ? idea.description : "");
            m.put("category", idea.category != null ? idea.category : "other");
            ideas.add(m);
        }

        return ideas;
    }

    /**
     * 3) getSubredditTrends
     * Returns trends sorted by engagement velocity.
     */
    public List<Map<String, Object>> getSubredditTrends() {
        List<RedditCrawlerService.PostDTO> posts = getAllPosts();
        Map<String, List<RedditCrawlerService.PostDTO>> bySubreddit = new LinkedHashMap<>();

        for (RedditCrawlerService.PostDTO p : posts) {
            String sub = "unknown";
            if (p.subreddit != null && !"".equals(p.subreddit)) {
                sub = p.subreddit.toLowerCase();
            }
            List<RedditCrawlerService.PostDTO> list = bySubreddit.computeIfAbsent(sub, k -> new ArrayList<>());
            list.add(p);
        }

        List<Map<String, Object>> trends = new ArrayList<>();
        for (Map.Entry<String, List<RedditCrawlerService.PostDTO>> entry : bySubreddit.entrySet()) {
            List<RedditCrawlerService.PostDTO> subPosts = entry.getValue();
            int totalPosts = subPosts.size();

            double sumScores = 0;
            double sumComments = 0;
            for (RedditCrawlerService.PostDTO p : subPosts) {
                sumScores += p.upvotes;
                sumComments += p.commentsCount;
            }
            double avgScore = totalPosts > 0 ? sumScores / totalPosts : 0;
            double avgComments = totalPosts > 0 ? sumComments / totalPosts : 0;

            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("subreddit", entry.getKey());
            trend.put("totalPosts", totalPosts);
            trend.put("avgScore", Math.round(avgScore * 10.0) / 10.0);
            trend.put("avgComments", avgScore > 0 ? Math.round(sumComments / totalPosts) : Math.round(avgComments));
            trend.put("engagementVelocity", calculateEngagementVelocity(subPosts));
            trend.put("categoryScores", nicheScorer.score(subPosts));

            trends.add(trend);
        }

        // Sort descending by engagement velocity
        trends.sort((a, b) -> Double.compare(
                ((Number) b.getOrDefault("engagementVelocity", 0.0)).doubleValue(),
                ((Number) a.getOrDefault("engagementVelocity", 0.0)).doubleValue()));

        return trends;
    }

    /**
     * 4) getKeywordFrequencies(int topN)
     * Returns keyword-frequency pairs for word-cloud display.
     */
    public List<Map<String, Object>> getKeywordFrequencies(int topN) {
        Map<String, Integer> freq = new TreeMap<>();

        // Stop-words to filter out
        Set<String> STOP_WORDS = Set.of(
                "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did", "will", "would", "could",
                "should", "may", "might", "shall", "can", "to", "of", "in", "for",
                "on", "with", "at", "by", "from", "as", "into", "through", "during",
                "before", "after", "and", "but", "or", "nor", "so", "yet", "both",
                "either", "neither", "not", "each", "every", "all", "any", "few",
                "more", "most", "other", "some", "such", "no", "only", "own", "same",
                "than", "too", "very", "just", "about", "above", "if", "it", "its",
                "my", "that", "this", "they", "we", "who", "what", "where", "when"
        );

        for (RedditCrawlerService.PostDTO post : getAllPosts()) {
            String text = buildFullText(post).toLowerCase();
            // Split on non-alphanumeric characters
            String[] words = text.split("[^a-z0-9]+");
            for (String word : words) {
                if (word.length() >= 3 && !STOP_WORDS.contains(word)) {
                    freq.merge(word, 1, Integer::sum);
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() >= 2) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("keyword", e.getKey());
                entry.put("frequency", e.getValue());
                result.add(entry);
            }
        }

        // Sort descending by frequency
        result.sort((a, b) -> Integer.compare(
                ((Number) b.get("frequency")).intValue(),
                ((Number) a.get("frequency")).intValue()));

        if (result.size() > topN) {
            result = result.subList(0, Math.min(topN, result.size()));
        }

        return result;
    }

    /**
     * 5) getSentimentHeatmapData
     * Returns positive/neutral/negative counts per subreddit as stacked percentages.
     */
    public List<Map<String, Object>> getSentimentHeatmapData() {
        Map<String, int[]> buckets = new LinkedHashMap<>();
        // bucket[0] = positive, [1] = neutral, [2] = negative

        for (RedditCrawlerService.PostDTO post : getAllPosts()) {
            String sub = "unknown";
            if (post.subreddit != null && !"".equals(post.subreddit)) {
                sub = post.subreddit.toLowerCase();
            }
            buckets.computeIfAbsent(sub, k -> new int[]{0, 0, 0});

            SentimentScore score = scoreSentiment(buildFullText(post));
            if ("positive".equals(score.sentiment)) {
                buckets.get(sub)[0]++;
            } else if ("negative".equals(score.sentiment)) {
                buckets.get(sub)[2]++;
            } else {
                buckets.get(sub)[1]++;
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : buckets.entrySet()) {
            String subreddit = entry.getKey();
            int[] counts = entry.getValue();
            int total = counts[0] + counts[1] + counts[2];

            Map<String, Object> hm = new LinkedHashMap<>();
            hm.put("subreddit", subreddit);
            hm.put("positive", counts[0]);
            hm.put("neutral", counts[1]);
            hm.put("negative", counts[2]);
            hm.put("total", total);
            if (total > 0) {
                hm.put("positivePercent", Math.round((double) counts[0] / total * 1000.0) / 10.0);
                hm.put("neutralPercent", Math.round((double) counts[1] / total * 1000.0) / 10.0);
                hm.put("negativePercent", Math.round((double) counts[2] / total * 1000.0) / 10.0);
            } else {
                hm.put("positivePercent", 0.0);
                hm.put("neutralPercent", 0.0);
                hm.put("negativePercent", 0.0);
            }
            result.add(hm);
        }

        return result;
    }

    /**
     * 6) getFullAnalyticsReport
     * Combines all analytics results + quick sentiment summary stat.
     */
    public Map<String, Object> getFullAnalyticsReport() {
        Map<String, Object> fullReport = new LinkedHashMap<>();

        // -- sentiment summary --
        List<SentimentScore> allScores = new ArrayList<>();
        for (RedditCrawlerService.PostDTO post : getAllPosts()) {
            allScores.add(scoreSentiment(buildFullText(post)));
        }
        long posCount = 0, negCount = 0, neuCount = 0;
        for (SentimentScore s : allScores) {
            if ("positive".equals(s.sentiment)) posCount++;
            else if ("negative".equals(s.sentiment)) negCount++;
            else neuCount++;
        }

        Map<String, Object> sentimentSummary = new LinkedHashMap<>();
        sentimentSummary.put("positive", posCount);
        sentimentSummary.put("negative", negCount);
        sentimentSummary.put("neutral", neuCount);
        sentimentSummary.put("total", allScores.size());
        if (allScores.size() > 0) {
            sentimentSummary.put("overallPositivePercent", Math.round(posCount * 100.0 / allScores.size()));
        } else {
            sentimentSummary.put("overallPositivePercent", 0);
        }
        fullReport.put("sentimentSummary", sentimentSummary);

        // -- sentiment details --
        fullReport.put("sentimentDetails", analyzeSentimentBySubreddit());

        // -- ideas --
        List<Map<String, Object>> ideas = extractIdeas(null);
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (Map<String, Object> idea : ideas) {
            String cat = (String) idea.get("category");
            if (cat != null && !"".equals(cat)) {
                categoryCounts.merge(cat, 1, Integer::sum);
            }
        }
        fullReport.put("ideaCategories", categoryCounts);
        fullReport.put("totalIdeas", ideas.size());
        // Truncate ideas to first 20 for the summary view
        List<Map<String, Object>> truncatedIdeas = ideas;
        if (ideas.size() > 20) {
            truncatedIdeas = ideas.subList(0, 20);
        }
        fullReport.put("ideas", truncatedIdeas);

        // -- trends --
        fullReport.put("trends", getSubredditTrends());

        // -- keywords --
        fullReport.put("keywords", getKeywordFrequencies(30));

        // -- heatmap --
        fullReport.put("heatmap", getSentimentHeatmapData());

        return fullReport;
    }

    /**
     * 7) summarizeSubreddit(String subreddit)
     * Returns tone, breakdown, top keywords, top ideas for a specific sub.
     */
    public Map<String, Object> summarizeSubreddit(String subreddit) {
        List<RedditCrawlerService.PostDTO> posts = getAllPosts().stream()
                .filter(p -> p.subreddit != null && p.subreddit.equalsIgnoreCase(subreddit))
                .collect(Collectors.toList());

        if (posts.isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "No data for subreddit: " + subreddit);
            return err;
        }

        // Sentiment counts
        long posCount = 0, negCount = 0, neuCount = 0;
        for (RedditCrawlerService.PostDTO p : posts) {
            String s = scoreSentiment(buildFullText(p)).sentiment;
            if ("positive".equals(s)) posCount++;
            else if ("negative".equals(s)) negCount++;
            else neuCount++;
        }

        // Tone
        String tone;
        if (posCount > negCount) tone = "positive";
        else if (negCount > posCount) tone = "negative";
        else tone = "neutral";

        // Top keywords for this subreddit only
        Set<String> STOP_WORDS_SUB = Set.of(
                "the", "a", "an", "is", "are", "was", "have", "has",
                "it", "this", "that", "and", "for", "with", "on", "in", "to"
        );
        Map<String, Integer> kwMap = new TreeMap<>();
        for (RedditCrawlerService.PostDTO p : posts) {
            String text = buildFullText(p).toLowerCase();
            for (String w : text.split("[^a-z0-9]+")) {
                if (w.length() >= 4 && !STOP_WORDS_SUB.contains(w)) {
                    kwMap.merge(w, 1, Integer::sum);
                }
            }
        }

        List<Map<String, Object>> topKeywords = kwMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> {
                    Map<String, Object> kw = new LinkedHashMap<>();
                    kw.put("keyword", e.getKey());
                    kw.put("count", e.getValue());
                    return kw;
                })
                .collect(Collectors.toList());

        // Top ideas for this sub (reuse heuristic extractor)
        List<Map<String, Object>> topIdeas = extractIdeasHeuristic().stream()
                .filter(i -> i.category != null && !"".equals(i.category))
                .limit(5)
                .map(i -> {
                    Map<String, Object> idea = new LinkedHashMap<>();
                    idea.put("title", i.title != null ? i.title : "");
                    idea.put("category", i.category != null ? i.category : "other");
                    idea.put("description", i.description != null ? i.description : "");
                    return idea;
                })
                .collect(Collectors.toList());

        // Sentiment breakdown
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("positive", posCount);
        breakdown.put("neutral", neuCount);
        breakdown.put("negative", negCount);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("subreddit", subreddit);
        summary.put("totalPosts", posts.size());
        summary.put("overallTone", tone);
        summary.put("sentimentBreakdown", breakdown);
        summary.put("topKeywords", topKeywords);
        summary.put("topIdeas", topIdeas);

        return summary;
    }

    /* ------------------------------------------------------------------ */
    /* SENTIMENT ANALYSIS - VADER-STYLE LEXICON                           */
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
            "very", 2.0,      "extremely", 2.2,   "really", 1.8,
            "super", 2.0,     "incredibly", 2.5,  "quite", 1.3,
            "slightly", -0.5, "barely", -0.7,
            "not", -1.0,      "never", -1.5
    ));

    /** Pure-Java VADER-style sentiment scoring. */
    private SentimentScore scoreSentiment(String text) {
        if (text == null || "".equals(text)) {
            return new SentimentScore("neutral", 0.0, 0, 0, 0);
        }

        String[] words = text.toLowerCase().split("[^a-z]+");
        double score = 0.0;
        int posCount = 0;
        int negCount = 0;

        for (int i = 0; i < words.length; i++) {
            if (words[i].length() < 2) continue;

            double modifier = 1.0;
            // Check previous word for intensifier
            if (i > 0 && INTENSIFIERS.containsKey(words[i - 1])) {
                modifier = INTENSIFIERS.get(words[i - 1]);
            }

            if (POSITIVE_WORDS.contains(words[i])) {
                score += modifier;
                posCount++;
            } else if (NEGATIVE_WORDS.contains(words[i])) {
                score -= modifier;
                negCount++;
            }
        }

        String sentiment;
        if (score > 0.5) sentiment = "positive";
        else if (score < -0.5) sentiment = "negative";
        else sentiment = "neutral";

        int wordCount = words.length;
        return new SentimentScore(sentiment, Math.abs(score), posCount, negCount, wordCount);
    }

    /* ------------------------------------------------------------------ */
    /* IDEA EXTRACTION                                                    */
    /* ------------------------------------------------------------------ */

    /** Regex patterns to detect user needs / project ideas. */
    private static final List<Pattern> NEED_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)(looking\\s+for\\s+(a\\s+)?)((?:(?:freelance|contract|remote|part-time|hiring))\\s+)?(developer|programmer|engineer|builder)"),
            Pattern.compile("(?i)(need\\s+(someone|help)|want\\s+to\\s+build|hiring)"),
            Pattern.compile("(?i)(does\\s+anyone\\s+know\\s+(how|of)|can\\s+someone\\s+(help|build|create))"),
            Pattern.compile("(?i)((?:start|build|create|launch)|(?:side|hobby)).*?(project|app|startup)"),
            Pattern.compile("(?i)(recommend.*(?:app|tool|library|framework))"),
            Pattern.compile("(?i)((?:just|simply|want|need)\\s+(to|for))\\s+((?:(?:build|create|make|develop|implement))\\s+.*?)[:.!?]")
    );

    private List<RawIdea> extractIdeasViaLLM() {
        List<RawIdea> results = new ArrayList<>();
        try {
            List<RedditCrawlerService.PostDTO> posts = getAllPosts().stream()
                    .limit(30)
                    .collect(Collectors.toList());
            if (posts.isEmpty()) return results;

            // Build context string
            StringBuilder ctx = new StringBuilder("Extract project ideas from these Reddit discussions:\n\n");
            for (RedditCrawlerService.PostDTO p : posts) {
                String title = p.title != null ? p.title : "";
                if ("".equals(title)) continue;
                ctx.append("- ").append(title);
                String body = p.body != null ? p.body : "";
                if (!"".equals(body)) {
                    String shortBody = body.length() > 150 ? body.substring(0, 150) + "..." : body;
                    ctx.append(" -- ").append(shortBody);
                }
                ctx.append("\n");
            }

            // Escape quotes for JSON embedding
            String escaped = ctx.toString().replace("\"", "\\\"").replace("\n", "\\n");

            String prompt = "{\n" +
                    "  \"model\": \"" + llmModel + "\",\n" +
                    "  \"messages\": [\n" +
                    "    {\"role\": \"system\", \"content\": \"Extract actionable project ideas and user needs from Reddit. Return ONLY a JSON array of objects with keys: title, description, category (one of: web-dev, mobile-app, ai-ml, devtools, infrastructure, education, design, data-science, game-dev, other).\"},\n" +
                    "    {\"role\": \"user\", \"content\": \"" + escaped + "\"}\n" +
                    "  ],\n" +
                    "  \"temperature\": 0.3\n" +
                    "}";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + llmApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(prompt, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    llmApiUrl + "/v1/chat/completions", entity, String.class);

            if (response.getStatusCode() != null &&
                response.getStatusCode().is2xxSuccessful() &&
                response.getBody() != null) {
                results.addAll(parseLlmIdeas(response.getBody()));
            }
        } catch (Exception ignored) {
            // LLM unavailable or error — fall back to heuristics only
        }
        return results;
    }

    /** Parse a raw JSON array of idea objects from an LLM response. */
    private List<RawIdea> parseLlmIdeas(String rawJson) {
        List<RawIdea> ideas = new ArrayList<>();
        if (rawJson == null) return ideas;

        int fb = rawJson.indexOf('[');
        int lb = rawJson.lastIndexOf(']');
        if (fb < 0 || lb <= fb) return ideas;

        try {
            Object parsed = JACKSON.readValue(rawJson.substring(fb, lb + 1), Object.class);
            if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> entryMap = (Map<String, Object>) item;
                        String title = extractString(entryMap.get("title"), "Untitled");
                        String desc = extractString(entryMap.get("description"), "");
                        Object catObj = entryMap.getOrDefault("category", "other");
                        String cat = catObj != null ? String.valueOf(catObj) : "other";
                        ideas.add(new RawIdea(title, desc, cat));
                    }
                }
            }
        } catch (Exception ignored) {
            // Failed to parse LLM JSON — that's fine, return empty
        }
        return ideas;
    }

    private String extractString(Object val, String fallback) {
        if (val != null && !"".equals(String.valueOf(val))) {
            return String.valueOf(val);
        }
        return fallback;
    }

    /** Heuristic idea extraction using regex patterns. */
    @SuppressWarnings("unchecked")
    private List<RawIdea> extractIdeasHeuristic() {
        List<RawIdea> ideas = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int idCounter = 0;

        for (RedditCrawlerService.PostDTO post : getAllPosts()) {
            String fullText = buildFullText(post).trim();
            if (fullText.length() < 30) continue;

            for (Pattern pat : NEED_PATTERNS) {
                Matcher m = pat.matcher(fullText);
                if (m.find()) {
                    String title = truncate(m.group(0), 80).trim();
                    if ("".equals(title) || title.length() < 20 || seen.contains(title.toLowerCase())) continue;

                    idCounter++;
                    seen.add(title.toLowerCase());
                    String description = truncate(matcherGroup(m), 100);
                    String bodyPart = post.body != null ? truncate(post.body, 100) : "";
                    if (!"".equals(bodyPart)) {
                        description = truncate(description + ". Context: " + bodyPart, 200);
                    }

                    ideas.add(new RawIdea(
                            "Project #" + idCounter + ": " + title,
                            description,
                            inferCategory(post.title, post.body)
                    ));
                    break; // Only one match per post
                }
            }
        }
        return ideas;
    }

    /** Get the matched regex group text. */
    private String matcherGroup(Matcher m) {
        if (m != null) return m.group(0);
        return "";
    }

    /** Infer category from title + body text patterns. */
    private String inferCategory(String title, String body) {
        String text = buildFullTextNonNull(title, body).toLowerCase();
        // Use the specific set of categories defined in the spec:
        // web-dev, mobile-app, ai-ml, devtools, infrastructure, education, design, data-science, game-dev, other
        if (text.contains("machine learning") || text.contains("ai ") || text.contains("llm") || text.contains("nlp")) {
            return "ai-ml";
        }
        if (text.contains("mobile") || text.contains("ios") || text.contains("android") || text.contains("flutter")) {
            return "mobile-app";
        }
        if (text.contains("react") || text.contains("next ") || text.contains("frontend") || text.contains("web ")) {
            return "web-dev";
        }
        if (text.contains("docker") || text.contains("kubernetes") || text.contains("aws") || text.contains("infra")) {
            return "infrastructure";
        }
        if (text.contains("tool") || text.contains("cli") || text.contains("vscode") || text.contains("extension ")) {
            return "devtools";
        }
        if (text.contains("edu") || text.contains("course") || text.contains("learn")) {
            return "education";
        }
        if (text.contains("design") || text.contains("ui ") || text.contains("ux ") || text.contains("figma")) {
            return "design";
        }
        if (text.contains("data") || text.contains("database") || text.contains("analytics")) {
            return "data-science";
        }
        if (text.contains("game") || text.contains("unity") || text.contains("unreal") || text.contains("godot")) {
            return "game-dev";
        }
        return "other";
    }

    /* ------------------------------------------------------------------ */
    /* INTERNAL HELPERS                                                   */
    /* ------------------------------------------------------------------ */

    /** Build title + body as a single working string. */
    private String buildFullText(RedditCrawlerService.PostDTO post) {
        if (post == null) return "";
        return (post.title != null ? post.title : "") + " " + (post.body != null ? post.body : "");
    }

    /** Build title + body without the extra null-check overhead. */
    private String buildFullTextNonNull(String title, String body) {
        return (title != null ? title : "") + " " + (body != null ? body : "");
    }

    /** Calculate engagement velocity: avg(upvotes + comments). */
    private double calculateEngagementVelocity(List<RedditCrawlerService.PostDTO> posts) {
        if (posts == null || posts.isEmpty()) return 0;
        long sum = 0;
        for (RedditCrawlerService.PostDTO p : posts) {
            sum += p.upvotes + p.commentsCount;
        }
        return Math.round((double) sum * 100.0 / posts.size() * 10) / 10.0;
    }

    /* ------------------------------------------------------------------ */
    /* INNER DATA CLASSES                                                 */
    /* ------------------------------------------------------------------ */

    /** Sentiment scoring result for a single text body. */
    public record SentimentScore(String sentiment, double confidence, int positiveCount, int negativeCount, int wordCount) {
    }

    /** Lightweight container for raw/hybrid extraction ideas. */
    static class RawIdea {
        String title;
        String description;
        String category;
        RawIdea(String title, String description, String category) {
            this.title = title;
            this.description = description;
            this.category = category;
        }
    }
}
