package com.redditcrawler.api.dto;

import java.util.List;

/**
 * DTO representing an extracted idea with relevance score and sentiment analysis.
 * Used for LLM-powered idea extraction from Reddit content.
 */
public class IdeaItem {

    /** Short descriptive title for the idea. */
    private String title;

    /** Concise summary of the idea content. */
    private String summary;

    /** Relevance score between 0.0 and 1.0. */
    private double relevanceScore;

    /** Sentiment: "positive", "negative", or "neutral". */
    private String sentiment;

    /** Keywords/phrases related to the idea. */
    private List<String> relatedKeywords;

    public IdeaItem() {}

    public IdeaItem(String title, String summary, double relevanceScore, String sentiment, List<String> relatedKeywords) {
        this.title = title;
        this.summary = summary;
        this.relevanceScore = relevanceScore;
        this.sentiment = sentiment;
        this.relatedKeywords = relatedKeywords;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public List<String> getRelatedKeywords() { return relatedKeywords; }
    public void setRelatedKeywords(List<String> relatedKeywords) { this.relatedKeywords = relatedKeywords; }
}
