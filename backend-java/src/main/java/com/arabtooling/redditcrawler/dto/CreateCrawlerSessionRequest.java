package com.arabtooling.redditcrawler.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreateCrawlerSessionRequest {
    private String subreddit;
    private String sort = "hot";
    private Integer postLimit = 25;
    private Integer depth = 1;
    private Boolean includeComments = true;
    private String keywords;
}
