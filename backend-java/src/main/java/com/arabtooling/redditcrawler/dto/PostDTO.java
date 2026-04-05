package com.arabtooling.redditcrawler.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PostDTO {
    private Long id;
    private String redditId;
    private Long scrapingSessionId;
    private String title;
    private String author;
    private String subreddit;
    private Integer upvotes;
    private String url;
    private String content;
    private LocalDateTime createdAt;
}
