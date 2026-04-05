package com.arabtooling.redditcrawler.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ScrapingSessionDTO {
    private Long id;
    private String subreddit;
    private String sort;
    private Integer postLimit;
    private Integer depth;
    private Boolean includeComments;
    private String keywords;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private List<Long> postIds;
}
