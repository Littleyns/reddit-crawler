package com.redditcrawler.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redditcrawler.api.entity.MetricsSnapshot;
import com.redditcrawler.api.repository.MetricsSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service that bridges TextAnalysisService analytics with long-term persistence,
 * enabling the frontend to query real historical data instead of mock payloads.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final int KEEP_DAYS = 90;

    private final MetricsSnapshotRepository snapshotRepo;
    private final TextAnalysisService textAnalysisService;
    private final ObjectMapper objectMapper;

    public AnalyticsService(MetricsSnapshotRepository snapshotRepo,
                            TextAnalysisService textAnalysisService) {
        this.snapshotRepo = snapshotRepo;
        this.textAnalysisService = textAnalysisService;
        this.objectMapper = new ObjectMapper();
    }

    /** Get full analytics report (merged from live analysis + persisted snapshots). */
    public Map<String, Object> getFullReport(String subreddit) {
        LocalDateTime after = LocalDateTime.now().minusDays(7);
        log.info("[ANALYTICS] Report request: subreddit={}, window=7d",
                subreddit != null ? subreddit : "global");

        // Pull live data from TextAnalysisService
        Map<String, Object> analysisData = buildFromAnalysis(subreddit);

        // Get persisted snapshots for history
        List<MetricsSnapshot> snapshots = snapshotRepo.findByTimeRange(after, LocalDateTime.now());

        long totalSnapshots = countTotalSnapshots(snapshots, subreddit);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("subreddit", subreddit != null ? subreddit : "global");
        report.put("windowDays", 7);
        report.put("fetchedAt", LocalDateTime.now().toString());
        report.put("snapshotCount", totalSnapshots);

        // Merge analysis data into report
        if (!analysisData.isEmpty()) {
            report.put("sentiment", analysisData.get("sentiment"));
            report.put("keywords", analysisData.get("keywords"));
            report.put("trends", analysisData.get("trends"));
        }

        // Persist new snapshot if none recent
        LocalDateTime latestTs = snapshots.stream()
                .map(MetricsSnapshot::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusDays(8));

        if (latestTs.isBefore(after)) {
            persistSnapshot(subreddit, "COMBINED", report);
        } else {
            log.info("[ANALYTICS] Snapshot already exists for latest window");
        }

        return report;
    }

    /** List tracked subreddits from persistence store. */
    public Set<String> getSubreddits() {
        LocalDateTime purgeAfter = LocalDateTime.now().minusDays(KEEP_DAYS);
        try {
            snapshotRepo.deleteByCreatedAtBefore(purgeAfter);
            log.info("[ANALYTICS] Purged snapshots older than {} days", KEEP_DAYS);
        } catch (Exception e) {
            log.warn("[ANALYTICS] Cleanup failed: {}", e.getMessage());
        }

        return snapshotRepo.findDistinctSubreddits();
    }

    /** Get historical analytics report for a subreddit over N days. */
    public List<Map<String, Object>> getHistoricalReport(String subreddit, int days) {
        if (days <= 0 || days > 365) {
            log.warn("[ANALYTICS] Invalid history window: {} days", days);
            return List.of();
        }

        LocalDateTime[] range = timeRange(days);
        List<MetricsSnapshot> snapshots = snapshotRepo.findByTimeRange(range[0], range[1]);

        return snapshots.stream()
                .map(s -> Map.<String, Object>of(
                        "createdAt", s.getCreatedAt().toString(),
                        "type", s.getSnapshotType(),
                        "meanSentiment", s.getMeanSentiment(),
                        "keywordData", s.getKeywordData(),
                        "additionalMetrics", s.getAdditionalMetrics()
                ))
                .collect(Collectors.toList());
    }

    // ---- private helpers --------------------------------------------------------

    private Map<String, Object> buildFromAnalysis(String subreddit) {
        try {
            List<Map<String, Object>> sentiment = textAnalysisService.analyzeSentimentBySubreddit();
            List<Map<String, Object>> keywords = textAnalysisService.getKeywordFrequencies(20);
            List<Map<String, Object>> trends = textAnalysisService.getSubredditTrends();

            return Map.of(
                    "sentiment", sentiment.isEmpty() ? List.<Map<String, Object>>of() : sentiment.get(0),
                    "keywords", keywords,
                    "trends", trends
            );
        } catch (Exception e) {
            log.warn("[ANALYTICS] TextAnalysisService unavailable: {}", e.getMessage());
            return Map.of();
        }
    }

    private void persistSnapshot(String subreddit, String type, Map<String, Object> data) {
        try {
            double meanSentiment = parseDouble(data.getOrDefault("meanSentiment", 0.0));
            String keywordJson = objectMapper.writeValueAsString(data.getOrDefault("keywords", List.<Map<String, Object>>of()));

            MetricsSnapshot snapshot = MetricsSnapshot.builder()
                    .subreddit(subreddit)
                    .snapshotType(type)
                    .meanSentiment(meanSentiment)
                    .keywordData(keywordJson)
                    .additionalMetrics(objectMapper.writeValueAsString(data))
                    .build();

            snapshotRepo.save(snapshot);
            log.info("[ANALYTICS] Persisted snapshot: id={}, type={}", snapshot.getId(), type);
        } catch (Exception e) {
            log.error("[ANALYTICS] Snapshot persistence failed: {}", e.getMessage());
        }
    }

    private long countTotalSnapshots(List<MetricsSnapshot> snapshots, String subreddit) {
        if (subreddit == null) {
            return (long) snapshots.size();
        }
        return (long) snapshots.stream()
                .filter(s -> s.getSubreddit() != null && s.getSubreddit().equalsIgnoreCase(subreddit))
                .count();
    }

    private double parseDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private LocalDateTime[] timeRange(int days) {
        return new LocalDateTime[]{LocalDateTime.now().minusDays(days), LocalDateTime.now()};
    }
}
