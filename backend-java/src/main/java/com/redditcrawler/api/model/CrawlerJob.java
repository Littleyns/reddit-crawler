package com.redditcrawler.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * JPA entity representing the state of a crawling job.
 */
@Entity
@Table(name = "crawler_jobs")
public class CrawlerJob {

    @Id
    @Column(nullable = false, length = 64)
    private String jobId;

    @Column(nullable = false, length = 256)
    private String subreddit;

    @Column(nullable = false, length = 32)
    private String status;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String config;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String resultsJson;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", updatable = false)
    private LocalDateTime updatedAt;

    public CrawlerJob() {
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getSubreddit() {
        return subreddit;
    }

    public void setSubreddit(String subreddit) {
        this.subreddit = subreddit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public String getResultsJson() {
        return resultsJson;
    }

    public void setResultsJson(String resultsJson) {
        this.resultsJson = resultsJson;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
