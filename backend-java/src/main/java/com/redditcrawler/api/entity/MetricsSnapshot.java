package com.redditcrawler.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persisted analytics snapshot: stores a point-in-time aggregation of
 * sentiment stats, keyword frequencies, subreddit trends, and engagement metrics.
 * Enables frontend to query historical data rather than mock/static payloads.
 */
@Entity
@Table(name = "metrics_snapshots",
       indexes = {
           @Index(name = "idx_snapshot_subreddit", columnList = "subreddit"),
           @Index(name = "idx_snapshot_created_at", columnList = "createdAt")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Subreddit this snapshot covers (null = cross-subreddit aggregate). */
    @Column(nullable = true, length = 100)
    private String subreddit;

    /** Snapshot type: SENTIMENT / KEYWORDS / ENGAGEMENT / TRENDS / COMBINED. */
    @Column(nullable = false, length = 30)
    private String snapshotType;

    /** Mean sentiment score in [-1.0, 1.0]. */
    @Column(columnDefinition = "DOUBLE PRECISION")
    private Double meanSentiment;

    /** JSON string of per-subreddit distribution map: {positive, neutral, negative}. */
    @Lob
    @Column(columnDefinition = "JSONB")
    private String sentimentDistribution;

    /** JSON string of keyword list: [{keyword, frequency}]. */
    @Lob
    @Column(columnDefinition = "JSONB")
    private String keywordData;

    /** Additional analytics data. */
    @Lob
    @Column(columnDefinition = "JSONB")
    private String additionalMetrics;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
