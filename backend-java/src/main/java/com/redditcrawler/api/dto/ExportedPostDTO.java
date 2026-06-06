package com.redditcrawler.api.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for query-based CSV export (POST body).
 */
public class ExportedPostDTO {

    private String subreddit;
    private String startDate;
    private String endDate;
    private String type;

    public ExportedPostDTO() {}

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
