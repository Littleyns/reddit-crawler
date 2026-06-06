package com.redditcrawler.api.entity;

import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Entity
@Table(name = "metrics_snapshots", indexes = {
    @Index(name = "idx_snapshot_subreddit", columnList = "subreddit"),
    @Index(name = "idx_snapshot_created_at", columnList = "createdAt")
})
public class MetricsSnapshot {
    private static final Logger log = LoggerFactory.getLogger(MetricsSnapshot.class);

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = true, length = 100) private String subreddit;
    @Column(nullable = false, length = 30) private String snapshotType;
    @Column(columnDefinition = "DOUBLE PRECISION") private Double meanSentiment;
    @Lob @Column(columnDefinition = "JSONB") private String sentimentDistribution;
    @Lob @Column(columnDefinition = "JSONB") private String keywordData;
    @Lob @Column(columnDefinition = "JSONB") private String additionalMetrics;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;

    public MetricsSnapshot() {}
    public MetricsSnapshot(String s, String t, Double ms, String sd, String kd, String am) {
        this.subreddit=s; this.snapshotType=t; this.meanSentiment=ms;
        this.sentimentDistribution=sd; this.keywordData=kd; this.additionalMetrics=am;
    }
    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private String subreddit, snapshotType, sentimentDistribution, keywordData, additionalMetrics;
        private Double meanSentiment;
        public Builder subreddit(String s){this.subreddit=s;return this;}
        public Builder snapshotType(String t){this.snapshotType=t;return this;}
        public Builder meanSentiment(Double v){this.meanSentiment=v;return this;}
        public Builder sentimentDistribution(String v){this.sentimentDistribution=v;return this;}
        public Builder keywordData(String v){this.keywordData=v;return this;}
        public Builder additionalMetrics(String v){this.additionalMetrics=v;return this;}
        public MetricsSnapshot build(){return new MetricsSnapshot(subreddit,snapshotType,meanSentiment,sentimentDistribution,keywordData,additionalMetrics);}
    }
    @PrePersist protected void onCreate(){if(this.createdAt==null)this.createdAt=LocalDateTime.now();}
    public Long getId(){return id;}public void setId(Long id){this.id=id;}
    public String getSubreddit(){return subreddit;}public void setSubreddit(String s){this.subreddit=s;}
    public String getSnapshotType(){return snapshotType;}public void setSnapshotType(String t){this.snapshotType=t;}
    public Double getMeanSentiment(){return meanSentiment;}public void setMeanSentiment(Double m){this.meanSentiment=m;}
    public String getSentimentDistribution(){return sentimentDistribution;}public void setSentimentDistribution(String s){this.sentimentDistribution=s;}
    public String getKeywordData(){return keywordData;}public void setKeywordData(String k){this.keywordData=k;}
    public String getAdditionalMetrics(){return additionalMetrics;}public void setAdditionalMetrics(String a){this.additionalMetrics=a;}
    public LocalDateTime getCreatedAt(){return createdAt;}public void setCreatedAt(LocalDateTime c){this.createdAt=c;}
}
