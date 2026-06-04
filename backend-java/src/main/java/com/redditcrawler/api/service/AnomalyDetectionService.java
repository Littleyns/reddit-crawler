package com.redditcrawler.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects statistical anomalies in crawler and analytics data.
 * Uses z-score based detection on windowed time-series slices of:
 *   - crawling velocity (posts/hour per subreddit)
 *   - sentiment score distributions
 *   - error rate trends
 *   - engagement spike detection
 */
@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);
    private static final double ZSCORE_THRESHOLD = 2.0; // ±2σ for anomaly flagging
    private static final int MIN_WINDOW_SIZE = 4;        // need at least N data points to establish baseline

    public record Anomaly(String type, String entity, double value, double zScore,
                          double mean, double stdDev, LocalDateTime detectedAt) {
        public boolean isSevere() {
            return Math.abs(zScore) > 3.0;
        }
        public String level() {
            return isSevere() ? "SEVERE" : "WARNING";
        }
    }

    public record AnomalyReport(List<Anomaly> anomalies, LocalDateTime windowStart,
                                LocalDateTime windowEnd, int dataPoints) {}

    // ---- z-score statistics helpers -------------------------------------------

    /** Compute mean of a numeric series. */
    private double computeMean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    /** Compute sample standard deviation. */
    private double computeStdDev(double[] values, double mean) {
        double sumSq = 0;
        for (double v : values) sumSq += (v - mean) * (v - mean);
        return Math.sqrt(sumSq / (values.length - 1));
    }

    /** Compute z-score of a value given its series stats. */
    private double computeZScore(double value, double mean, double stdDev) {
        if (stdDev == 0) return 0; // zero variance → no anomaly possible
        return (value - mean) / stdDev;
    }

    /** Check if a single value is anomalous vs its baseline. */
    private boolean isAnomaly(double zScore) {
        return Math.abs(zScore) > ZSCORE_THRESHOLD;
    }

    // ---- anomaly detectors ----------------------------------------------------

    /**
     * Detect velocity anomalies for crawled posts over time windows.
     * A velocity spike or drop beyond ±2σ from the rolling mean is flagged.
     */
    public List<Anomaly> detectVelocityAnomalies(Map<String, List<Double>> hourlyPosts) {
        List<Anomaly> anomalies = new ArrayList<>();

        for (Map.Entry<String, List<Double>> entry : hourlyPosts.entrySet()) {
            String subreddit = entry.getKey();
            List<Double> valuesList = entry.getValue();
            if (valuesList.size() < MIN_WINDOW_SIZE) continue;

            double[] values = valuesList.stream().mapToDouble(Double::doubleValue).toArray();
            int lastIdx = values.length - 1;
            double currentVelocity = values[lastIdx];

            // Baseline: exclude the current point from its own baseline calculation
            double[] baseValues = new double[lastIdx];
            System.arraycopy(values, 0, baseValues, 0, lastIdx);

            double mean = computeMean(baseValues);
            double stdDev = (baseValues.length > 1) ? computeStdDev(baseValues, mean) : 0;
            if (stdDev == 0 && mean > 0) stdDev = mean * 0.15; // heuristic fallback

            double zScore = computeZScore(currentVelocity, mean, stdDev);

            if (isAnomaly(zScore)) {
                Anomaly a = new Anomaly(
                        "VELOCITY_SPIKE", subreddit, currentVelocity, zScore, mean, stdDev,
                        LocalDateTime.now()
                );
                anomalies.add(a);
                log.info("[ANOMALY-VELOCITY] Subr={}|val={}|z={:.3f}|mean={:.2f}",
                        subreddit, String.format("%.1f", currentVelocity), zScore, mean);
            }
        }

        return anomalies;
    }

    /**
     * Detect sentiment score anomalies for a given set of scores.
     * Compares the latest batch average against its historical window.
     */
    public List<Anomaly> detectSentimentAnomalies(Map<String, List<Double>> hourlyAvgScores) {
        List<Anomaly> anomalies = new ArrayList<>();

        for (Map.Entry<String, List<Double>> entry : hourlyAvgScores.entrySet()) {
            String subreddit = entry.getKey();
            List<Double> valuesList = entry.getValue();
            if (valuesList.size() < MIN_WINDOW_SIZE) continue;

            double[] values = valuesList.stream().mapToDouble(Double::doubleValue).toArray();
            int lastIdx = values.length - 1;
            double currentAvg = values[lastIdx];

            // Baseline from prior hours
            double[] baseValues = new double[lastIdx];
            System.arraycopy(values, 0, baseValues, 0, lastIdx);

            double mean = computeMean(baseValues);
            double stdDev = (baseValues.length > 1) ? computeStdDev(baseValues, mean) : 0.05;
            int sign = Double.compare(currentAvg - mean, 0);
            if (sign == 0) stdDev = 0.05; // avoid zero if identical

            double zScore = computeZScore(currentAvg, mean, Math.abs(stdDev));

            if (isAnomaly(zScore)) {
                Anomaly anom;
                String direction = currentAvg > mean ? "SEESAW_UP" : "SEESAW_DOWN";
                String type = "SENTIMENT_SWING_" + direction;
                anom = new Anomaly(type, subreddit, currentAvg, zScore, mean, Math.abs(stdDev),
                        LocalDateTime.now());
                anomalies.add(anom);
                log.info("[ANOMALY-SENTIMENT] {}|subr={}|z={:.3f}|dir={}",
                        String.format("%.3f", zScore), subreddit, direction);
            }
        }

        return anomalies;
    }

    /**
     * Detect error-rate spikes in crawler jobs.
     * Compares recent error rate against historical running average.
     */
    public List<Anomaly> detectErrorRateAnomalies(List<Map<String, Object>> jobBatches) {
        List<Anomaly> anomalies = new ArrayList<>();
        if (jobBatches.size() < MIN_WINDOW_SIZE) return anomalies;

        // Extract error rates: batch → %{ failed / total }
        double[] errorRates = new double[jobBatches.size()];
        for (int i = 0; i < jobBatches.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> batch = jobBatches.get(i);
            long total = ((Number) batch.getOrDefault("totalJobs", 0)).longValue();
            long failed = ((Number) batch.getOrDefault("failedJobs", 0)).longValue();
            errorRates[i] = total > 0 ? (double) failed / total * 100.0 : 0.0;
        }

        int lastIdx = errorRates.length - 1;
        double currentRate = errorRates[lastIdx];
        double[] baseValues = new double[lastIdx];
        System.arraycopy(errorRates, 0, baseValues, 0, lastIdx);

        double mean = computeMean(baseValues);
        double stdDev = (baseValues.length > 1) ? computeStdDev(baseValues, mean) : 0.5;
        if (stdDev == 0 && mean > 0) stdDev = Math.max(mean * 0.2, 0.5);

        double zScore = computeZScore(currentRate, mean, stdDev);

        if (currentRate > mean && isAnomaly(zScore)) {
            Anomaly a = new Anomaly("ERROR_RATE_SPIKE", "overall", currentRate,
                    zScore, mean, stdDev, LocalDateTime.now());
            anomalies.add(a);
            log.warn("[ANOMALY-ERROR] rate={:.1f}%|z={:.3f}|baseline={:.1f}%",
                    currentRate, zScore, mean);
        }

        return anomalies;
    }

    /**
     * Detect engagement spikes for comments-per-post patterns.
     */
    public List<Anomaly> detectEngagementAnomalies(List<Double> hourlyEngagement) {
        // Same pattern as velocity; just renamed semantics
        if (hourlyEngagement.size() < MIN_WINDOW_SIZE) return new ArrayList<>();

        double[] values = hourlyEngagement.stream().mapToDouble(Double::doubleValue).toArray();
        int lastIdx = values.length - 1;
        double currentVal = values[lastIdx];

        double[] baseValues = new double[lastIdx];
        System.arraycopy(values, 0, baseValues, 0, lastIdx);

        double mean = computeMean(baseValues);
        double stdDev = (baseValues.length > 1) ? computeStdDev(baseValues, mean) :
                Math.max(currentVal * 0.3, 0.5);

        double zScore = computeZScore(currentVal, mean, Math.abs(stdDev));

        if (isAnomaly(zScore)) {
            return List.of(new Anomaly("ENGAGEMENT_OUTLIER", "overall", currentVal,
                    zScore, mean, stdDev, LocalDateTime.now()));
        }
        return new ArrayList<>();
    }

    // ---- unified detector -----------------------------------------------------

    /**
     * Run all anomaly detectors on the provided metric data and return a consolidated report.
     */
    public AnomalyReport detectAll(Map<String, List<Double>> velocityData,
                                    Map<String, List<Double>> sentimentData,
                                    List<Map<String, Object>> errorBatches,
                                    List<Double> engagement) {
        List<Anomaly> all = new ArrayList<>();
        all.addAll(detectVelocityAnomalies(velocityData));
        all.addAll(detectSentimentAnomalies(sentimentData));
        all.addAll(detectErrorRateAnomalies(errorBatches));
        all.addAll(detectEngagementAnomalies(engagement));

        // Sort by severity (severe first), then by |z-score| descending
        all.sort((a, b) -> {
            int sev = Boolean.compare(b.isSevere(), a.isSevere());
            if (sev != 0) return sev;
            return Double.compare(Math.abs(b.zScore()), Math.abs(a.zScore()));
        });

        log.info("[ANOMALY-REPORT] Total anomalies detected: {} [severe={}}]",
                all.size(), java.lang.Math.toIntExact(all.stream().filter(Anomaly::isSevere).count()));
        return new AnomalyReport(all, LocalDateTime.now().minusHours(24), LocalDateTime.now(), all.size());
    }

    /** Helper: create a report from pre-populated metric maps. */
    public AnomalyReport buildReport(Map<String, List<Double>> velocityData,
                                      Map<String, List<Double>> sentimentData,
                                      List<Map<String, Object>> errorBatches,
                                      List<Double> engagement) {
        return detectAll(velocityData, sentimentData, errorBatches, engagement);
    }
}
