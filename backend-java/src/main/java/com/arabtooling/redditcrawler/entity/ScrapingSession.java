package com.arabtooling.redditcrawler.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scraping_sessions")
public class ScrapingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String subreddit;

    @Column(length = 20)
    private String sort = "hot";

    @Column
    private Integer postLimit = 25;

    @Column
    private Integer depth = 1;

    @Column
    private Boolean includeComments = true;

    @Column(length = 255)
    private String keywords;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    @JsonIgnoreProperties("sessions")
    private User creator;

    @OneToMany(mappedBy = "scrapingSession", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Post> posts = new java.util.ArrayList<>();

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
    }
}
