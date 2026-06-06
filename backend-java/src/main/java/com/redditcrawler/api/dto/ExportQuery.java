package com.redditcrawler.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for query-based CSV export (POST body).
 */
public class ExportQuery {

    private String subreddit;
    private String startDate;  // ISO-8601 or epoch string
    private String endDate;    // ISO-8601 or epoch string
    private String type;       // "post" | "comment" (nullable = all)

    public ExportQuery() {}

    @JsonProperty("subreddit")
    public String getSubreddit() { return subreddit; }
    public void setSubreddit(String subreddit) { this.subreddit = subreddit; }

    @JsonProperty("startDate")
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    @JsonProperty("endDate")
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    @JsonProperty("type")
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
