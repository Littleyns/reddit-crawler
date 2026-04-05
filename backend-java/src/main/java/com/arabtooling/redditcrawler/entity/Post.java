package com.arabtooling.redditcrawler.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reddit_id", nullable = false, length = 20)
    private String redditId;

    @Column(name = "scraping_session_id", nullable = false)
    private Long scrapingSessionId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 100)
    private String author = "[deleted]";

    @Column(nullable = false, length = 100)
    private String subreddit;

    @Column(name = "upvotes", columnDefinition = "integer default 0")
    private Integer upvotes = 0;

    @Column(length = 1000)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scraping_session_id", insertable = false, updatable = false)
    private ScrapingSession scrapingSession;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments;
}
