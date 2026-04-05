package com.arabtooling.redditcrawler.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reddit_id", nullable = false, length = 20)
    private String redditId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(nullable = false, length = 100)
    private String author = "[deleted]";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(columnDefinition = "integer default 0")
    private Integer upvotes = 0;

    @Column
    private Integer depth = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", insertable = false, updatable = false)
    private Post post;
}
