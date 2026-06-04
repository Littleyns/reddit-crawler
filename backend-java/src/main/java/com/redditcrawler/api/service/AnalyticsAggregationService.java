package com.redditcrawler.api.service;

import com.redditcrawler.api.model.CrawlerJob;
import com.redditcrawler.api.model.Post;
import com.redditcrawler.api.repository.CrawlerJobRepository;
import com.redditcrawler.api.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Aggregates data from PostRepository and CrawlerJobRepository
 * to produce dashboard analytics, subreddit health metrics,
 * engagement stats, and crawl activity summaries.
 */
@Service
public class AnalyticsAggregationService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAggregationService.class);

    private final PostRepository postRepository;
    private final CrawlerJobRepository jobRepository;

    public AnalyticsAggregationService(PostRepository postRepository, CrawlerJobRepository jobRepository) {
        this.postRepository = postRepository;
        this.jobRepository = jobRepository;
    }

    // ================================================================
    // DASHBOARD SUMMARY
    // ================================================================

    /** Produces the full dashboard stats object. */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        long totalPosts = postRepository.count();
        List<Object[]> subCounts = postRepository.countPostsBySubreddit();
        int totalSubreddits = subCounts.size();

        stats.put("totalPosts", totalPosts);
        stats.put("totalSubreddits", totalSubreddits);

        Integer totalUpvotes = postRepository.totalUpvotesBySubreddit(null);
        stats.put("totalUpvotes", totalUpvotes != null ? totalUpvotes : 0);

        Set<String> allSubs = postRepository.findAllSubreddits();
        stats.put("subreddits", new ArrayList<>(allSubs));

        // Recent activity (last 30 days)
        Instant thirtyDaysAgo = Instant.now().minusSeconds(TimeUnit.DAYS.toSeconds(30));
        List<Object[]> recentActivityRaw = jobRepository.activeSubredditsInDateRange(thirtyDaysAgo, Instant.now());
        Map<String, Long> recentActivity = new LinkedHashMap<>();
        for (Object[] row : recentActivityRaw) {
            if (row.length >= 2) {
                recentActivity.put(String.valueOf(row[0]), (Long) row[1]);
            }
        }
        stats.put("recentSubredditActivity", recentActivity);

        // Crawl success rate
        List<Object[]> sfCounts = jobRepository.successFailureCounts();
        int successCount = 0, failureCount = 0;
        for (Object[] row : sfCounts) {
            if (row.length >= 2) {
                int isCompleted = (Integer) row[0];
                long count = (Long) row[1];
                if (isCompleted == 1) successCount = (int) count;
                else failureCount = (int) count;
            }
        }
        stats.put("successfulCrawls", successCount);
        stats.put("failedCrawls", failureCount);
        int totalJobs = successCount + failureCount;
        stats.put("totalJobs", totalJobs);
        stats.put("successRate", totalJobs > 0 ? Math.round((double) successCount / totalJobs * 100) : 0);

        // Top 10 subreddits by post count
        List<Map<String, Object>> topSubs = new ArrayList<>();
        for (int i = 0; i < Math.min(10, subCounts.size()); i++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("subreddit", subCounts.get(i)[0]);
            entry.put("postCount", subCounts.get(i)[1]);
            Double avgScore = postRepository.avgUpvotesBySubreddit(String.valueOf(subCounts.get(i)[0]));
            entry.put("avgUpvotes", avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0);
            topSubs.add(entry);
        }
        stats.put("topSubreddits", topSubs);

        return stats;
    }

    // ================================================================
    // SUBREDDIT HEALTH METRICS
    // ================================================================

    /** Compute health metrics for a single subreddit. */
    public Map<String, Object> getSubredditHealth(String subreddit) {
        List<Post> posts = postRepository.findBySubreddit(subreddit);
        if (posts.isEmpty()) {
            return Map.of("subreddit", subreddit, "error", "No data found");
        }

        int totalUpvotes = 0;
        int maxUpvotes = Integer.MIN_VALUE;
        int minUpvotes = Integer.MAX_VALUE;
        long sumComments = 0;
        Instant oldest = null, newest = null;

        for (Post p : posts) {
            totalUpvotes += p.getUpvotes();
            if (p.getUpvotes() > maxUpvotes) maxUpvotes = p.getUpvotes();
            if (p.getUpvotes() < minUpvotes) minUpvotes = p.getUpvotes();
            sumComments += p.getCommentsCount();
            Instant created = p.getCreatedUtc();
            if (oldest == null || (created != null && created.isBefore(oldest))) oldest = created;
            if (newest == null || (created != null && created.isAfter(newest))) newest = created;
        }

        double avgUpvotes = totalUpvotes / (double) posts.size();
        double avgComments = sumComments / posts.size();
        double engagementVelocity = Math.round((avgUpvotes + avgComments) * 10.0) / 10.0;

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("subreddit", subreddit);
        health.put("totalPosts", posts.size());
        health.put("avgUpvotes", Math.round(avgUpvotes * 10.0) / 10.0);
        health.put("maxUpvotes", maxUpvotes);
        health.put("minUpvotes", minUpvotes);
        health.put("avgCommentsPerPost", Math.round(avgComments * 10.0) / 10.0);
        health.put("engagementVelocity", engagementVelocity);
        health.put("dateRangeStart", oldest);
        health.put("dateRangeEnd", newest);

        long daysInRange = (oldest != null && newest != null) ?
                Duration.between(oldest, newest).toDays() + 1 : 1;
        double dailyActivity = Math.round(posts.size() / (double) daysInRange * 10.0) / 10.0;
        health.put("activityLevel", dailyActivity);

        String level;
        if (dailyActivity >= 10) level = "high";
        else if (dailyActivity >= 3) level = "medium";
        else level = "low";
        health.put("activityClassification", level);

        return health;
    }

    // ================================================================
    // ENGAGEMENT ANALYSIS
    // ================================================================

    /** Top N posts by upvotes across all subreddits (or filtered by subreddit). */
    public List<Map<String, Object>> getTopPosts(String subreddit, int n) {
        if (subreddit != null && !subreddit.isEmpty()) {
            List<Post> page = postRepository.findBySubredditOrSearch(subreddit, null,
                    org.springframework.data.domain.PageRequest.of(0, n)).getContent();
            return wrapPostList(page, n);
        }
        return wrapPostList(postRepository.findByUpvotesGreaterThanEqualOrderByUpvotesDesc(0), n);
    }

    /** Posts with least engagement. */
    public List<Map<String, Object>> getLowEngagementPosts(String subreddit, Integer upvoteThreshold) {
        int threshold = (upvoteThreshold != null && upvoteThreshold > 0) ? upvoteThreshold : 0;
        if (subreddit != null && !subreddit.isEmpty()) {
            List<Post> page = postRepository.findBySubredditOrSearch(subreddit, null,
                    org.springframework.data.domain.PageRequest.of(0, 50)).getContent();
            return wrapPostList(page.stream()
                    .filter(p -> p.getUpvotes() < threshold)
                    .collect(Collectors.toList()), threshold);
        }
        return wrapPostList(postRepository.findLowEngagementPosts(threshold), threshold);
    }

    /** Posts with highest comments-to-upvote ratio for discussion-heavy finds. */
    public Map<String, Object> getHighDiscussionPosts(String subreddit, int n) {
        List<Post> posts;
        if (subreddit != null && !subreddit.isEmpty()) {
            posts = postRepository.findBySubredditOrSearch(subreddit, null,
                    org.springframework.data.domain.PageRequest.of(0, n + 50)).getContent();
        } else {
            posts = postRepository.findAll(org.springframework.data.domain.PageRequest.of(0, n + 50)).getContent();
        }

        List<Map<String, Object>> scored = new ArrayList<>();
        for (Post p : posts) {
            double ratio;
            if (p.getUpvotes() == null || p.getUpvotes() <= 0) {
                ratio = p.getCommentsCount() != null ? p.getCommentsCount() : 0;
            } else {
                ratio = p.getCommentsCount() / ((double) p.getUpvotes() + 1);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("subreddit", p.getSubreddit());
            item.put("title", p.getTitle());
            item.put("upvotes", p.getUpvotes());
            item.put("commentsCount", p.getCommentsCount());
            item.put("author", p.getAuthor());
            item.put("discussionRatio", Math.round(ratio * 100.0) / 100.0);
            scored.add(item);
        }

        scored.sort((a, b) -> Double.compare(
                ((Number) b.get("discussionRatio")).doubleValue(),
                ((Number) a.get("discussionRatio")).doubleValue()));

        return Map.of(
                "subreddit", subreddit != null ? subreddit : "all",
                "highDiscussionPosts", scored.stream().limit(n).collect(Collectors.toList()),
                "totalCandidates", scored.size()
        );
    }

    // ================================================================
    // CRAWL ACTIVITY ANALYSIS
    // ================================================================

    /** Recent activity: last N completed crawl sessions. */
    public List<Map<String, Object>> getRecentCrawls(int n) {
        Instant cutoff = Instant.now().minusSeconds(TimeUnit.DAYS.toSeconds(365));
        return jobRepository.findRecentJobs(cutoff).stream()
                .limit(n)
                .map(this::wrapJob)
                .collect(Collectors.toList());
    }

    /** Crawl success/failure breakdown by subreddit. */
    public Map<String, Object> getCrawlBreakdownBySubreddit() {
        List<Object[]> subCounts = jobRepository.countJobsBySubreddit();
        List<Map<String, Object>> breakdown = new ArrayList<>();
        for (Object[] row : subCounts) {
            String sub = String.valueOf(row[0]);
            long completed = jobRepository.countByStatusAndSubreddit("COMPLETED", sub);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("subreddit", sub);
            entry.put("jobs", row[1]);
            entry.put("completed", completed);
            breakdown.add(entry);
        }
        return Map.of("breakdown", breakdown);
    }

    /** Average crawl duration per subreddit. */
    public Map<String, Object> getCrawlDurationStats() {
        List<Object[]> durations = jobRepository.avgJobDurationBySubreddit();
        List<Map<String, Object>> detail = new ArrayList<>();
        double totalAvg = 0;
        for (Object[] row : durations) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("subreddit", row[0]);
            double durSecs = (double) row[1];
            entry.put("avgDurationSeconds", Math.round(durSecs * 10.0) / 10.0);
            if (durSecs < 60) entry.put("durationHuman", Math.round(durSecs) + "s");
            else if (durSecs < 3600) entry.put("durationHuman", Math.round(durSecs / 60.0 * 10.0) / 10.0 + " min");
            else entry.put("durationHuman", Math.round(durSecs / 3600.0 * 10.0) / 10.0 + " hrs");
            totalAvg += durSecs;
            detail.add(entry);
        }
        double overallAvg = durations.isEmpty() ? 0 : totalAvg / durations.size();
        return Map.of(
                "bySubreddit", detail,
                "overallAverageSeconds", Math.round(overallAvg * 10.0) / 10.0
        );
    }

    // ================================================================
    // TIME-BASED ANALYSIS
    // ================================================================

    /** Post volume by subreddit for the last N days. */
    public Map<String, Object> getPostVolumeBySubreddit(int days) {
        Instant end = Instant.now();
        Instant start = Instant.ofEpochSecond(System.currentTimeMillis() - (long) days * 86400);
        List<Object[]> volumes = jobRepository.activeSubredditsInDateRange(start, end);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : volumes) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("subreddit", row[0]);
            entry.put("postsAdded", row[1]);
            result.add(entry);
        }
        result.sort((a, b) -> Integer.compare(
                ((Number) b.get("postsAdded")).intValue(),
                ((Number) a.get("postsAdded")).intValue()));
        return Map.of("days", days, "volumeBySubreddit", result);
    }

    /** Posts added per day (last 30 days). */
    public List<Map<String, Object>> getDailyPostTrend() {
        Instant end = Instant.now();
        Instant start = Instant.ofEpochSecond(System.currentTimeMillis() - 86400L * 30);
        Set<String> subreddits = postRepository.findAllSubreddits();

        List<Map<String, Object>> trend = new ArrayList<>();
        for (String sub : subreddits) {
            List<Post> postsBySub = postRepository.findByDateRange(start, end).stream()
                    .filter(p -> p.getSubreddit().equals(sub))
                    .collect(Collectors.toList());

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("subreddit", sub);
            entry.put("dailyCount", postsBySub.size());
            trend.add(entry);
        }
        return trend;
    }

    // ================================================================
    // INTERNAL HELPERS
    // ================================================================

    private Map<String, Object> wrapJob(CrawlerJob job) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobId", job.getJobId());
        m.put("subreddit", job.getSubreddit());
        m.put("status", job.getStatus());
        m.put("startedAt", job.getStartedAt());
        m.put("completedAt", job.getCompletedAt());
        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            double dur = Duration.between(job.getStartedAt(), job.getCompletedAt()).getSeconds();
            m.put("durationSeconds", Math.round(dur * 10.0) / 10.0);
        }
        return m;
    }

    private List<Map<String, Object>> wrapPostList(List<Post> posts, int n) {
        return posts.stream()
                .limit(n > 0 ? n : Long.MAX_VALUE)
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("subreddit", p.getSubreddit());
                    m.put("title", p.getTitle());
                    String body = p.getBody();
                    m.put("body", body != null ? (body.length() > 200 ? body.substring(0, 200) + "..." : body) : "");
                    m.put("author", p.getAuthor());
                    m.put("upvotes", p.getUpvotes());
                    m.put("commentsCount", p.getCommentsCount());
                    m.put("createdUtc", p.getCreatedUtc());
                    m.put("permalink", p.getPermalink());
                    m.put("url", p.getUrl());
                    return m;
                })
                .collect(Collectors.toList());
    }
}
