package com.arabtooling.redditcrawler.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CommentDTO {
    private Long id;
    private String redditId;
    private Long postId;
    private Long parentCommentId;
    private String author;
    private String body;
    private Integer upvotes;
    private Integer depth;
    private LocalDateTime createdAt;
}
