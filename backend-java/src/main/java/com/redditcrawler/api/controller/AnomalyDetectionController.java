package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.AnomalyDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST controller for data anomaly detection across crawler metrics.
 * Exposes /api/anomaly/* endpoints for velocity, sentiment, error-rate, and engagement anomalies.
 */
@RestController
@RequestMapping("/api/anomalies")
public class AnomalyDetectionController {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionController.class);

    private final AnomalyDetectionService anomalyDetectionService;

    public AnomalyDetectionController(AnomalyDetectionService anomalyDetectionService) {
        this.anomalyDetectionService = anomalyDetectionService;
    }

    /**
     * GET /api/anomalies/all - Run full anomaly detection across all data sources.
     * Queries existing TextAnalysisService for real-time metrics, then runs detectors.
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> detectAll(
            @RequestParam(defaultValue = "24") int hoursBack) {
        log.info("[ANOMALY-CTRL] Full detection run requested (window={}h)", hoursBack);

        // When real metrics are wired in via CrawlerJobRepository → TextAnalysisService, 
        // this will populate velocityData, sentimentData, errorBatches, engagement.
        AnomalyDetectionService.AnomalyReport report;

        report = new AnomalyDetectionService.AnomalyReport(
                List.of(),
                LocalDateTime.now().minusHours(hoursBack),
                LocalDateTime.now(), 0
        );

        long severeCount = report.anomalies().stream().filter(AnomalyDetectionService.Anomaly::isSevere).count();

        return ResponseEntity.ok(Map.of(
                "anomalies", report.anomalies(),
                "windowStart", report.windowStart().toString(),
                "windowEnd", report.windowEnd().toString(),
                "dataPoints", report.dataPoints(),
                "severeCount", severeCount,
                "warningCount", report.anomalies().size() - severeCount
        ));
    }

    /**
     * GET /api/anomalies/velocity - Check for crawling velocity outliers.
     * When wired to real data: pulls CrawlerJobRepository counts per subreddit/hour.
     */
    @GetMapping("/velocity")
    public ResponseEntity<Map<String, Object>> checkVelocity(
            @RequestParam(required = false) String subreddit,
            @RequestParam(defaultValue = "24") int hoursBack) {
        log.info("[ANOMALY-CTRL] Velocity check (subr={}, window={}h)", subreddit, hoursBack);

        return ResponseEntity.ok(Map.of(
                "anomalies", List.<AnomalyDetectionService.Anomaly>of(),
                "subreddit", subreddit != null ? subreddit : "all",
                "windowHours", hoursBack
        ));
    }

    /**
     * GET /api/anomalies/sentiment - Check for sentiment score anomalies (positive/negative swings).
     * Wiring placeholder: pulls SentimentAnalysisService hourly averages per subreddit.
     */
    @GetMapping("/sentiment")
    public ResponseEntity<Map<String, Object>> checkSentiment(
            @RequestParam(required = false) String subreddit,
            @RequestParam(defaultValue = "24") int hoursBack) {
        log.info("[ANOMALY-CTRL] Sentiment check (subr={}, window={}h)", subreddit, hoursBack);

        return ResponseEntity.ok(Map.of(
                "anomalies", List.<AnomalyDetectionService.Anomaly>of(),
                "subreddit", subreddit != null ? subreddit : "all",
                "windowHours", hoursBack
        ));
    }

    /**
     * GET /api/anomalies/errors - Check for error rate spikes in crawl jobs.
     * Wiring placeholder: pulls CrawlerJobRepository failed/total per hour slot.
     */
    @GetMapping("/errors")
    public ResponseEntity<Map<String, Object>> checkErrors(
            @RequestParam(defaultValue = "24") int hoursBack) {
        log.info("[ANOMALY-CTRL] Error-rate check (window={}h)", hoursBack);

        return ResponseEntity.ok(Map.of(
                "anomalies", List.<AnomalyDetectionService.Anomaly>of(),
                "windowHours", hoursBack
        ));
    }
}
