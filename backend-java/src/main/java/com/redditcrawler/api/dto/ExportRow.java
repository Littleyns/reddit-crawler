package com.redditcrawler.api.dto;

import java.time.Instant;

/**
 * DTO for CSV export rows — one crawled Reddit post with metadata.
 */
public class ExportRow {

    private String title;
    private String url;        // permalink
    private String subreddit;
    private String type;       // "post" or "comment"
    private String sentiment;  // positive / neutral / negative
    private String keywords;   // comma-separated keywords
    private String timestamp;  // createdAt as ISO-8601 string

    public ExportRow() {}

    public ExportRow(String title, String url, String subreddit, String type,
                     String sentiment, String keywords, String timestamp) {
        this.title = title;
        this.url = url;
        this.subreddit = subreddit;
        this.type = type;
        this.sentiment = sentiment != null ? sentiment : "neutral";
        this.keywords = keywords != null ? keywords : "";
        this.timestamp = timestamp != null ? timestamp : "";
    }

    public static String[] getHeaders() {
        return new String[]{"title", "url", "subreddit", "type", "sentiment", "keywords", "timestamp"};
    }

    // ---- getters / setters ----

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getSubreddit() { return subreddit; }
    public void setSubreddit(String subreddit) { this.subreddit = subreddit; }

    public String getType() { return type; }
    public void setType(String type) { this.type = "post".equalsIgnoreCase(type) ? "POST" : "COMMENT"; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment != null ? sentiment : "neutral"; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords != null ? keywords : ""; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp != null ? timestamp : ""; }
}
