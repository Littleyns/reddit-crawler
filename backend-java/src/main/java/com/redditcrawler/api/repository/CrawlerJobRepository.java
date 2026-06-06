package com.redditcrawler.api.repository;

import com.redditcrawler.api.model.CrawlerJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * JPA repository for CrawlerJob entities — tracks crawl job states.
 */
@Repository
public interface CrawlerJobRepository extends JpaRepository<CrawlerJob, String> {

    // -----------------------------------------------------------------
    // STATUS-BASED QUERIES
    // -----------------------------------------------------------------

    /** Find all jobs in a given status (PENDING, RUNNING, COMPLETED, FAILED). */
    List<CrawlerJob> findByStatus(@Param("status") String status);

    /** Paginated jobs ordered by start date descending. */
    @Query("SELECT j FROM CrawlerJob j WHERE (:subreddit IS NULL OR j.subreddit = :subreddit) " +
           "ORDER BY j.startedAt DESC")
    Page<CrawlerJob> findBySubredditOrderedByDate(
            @Param("subreddit") String subreddit,
            Pageable pageable);

    // -----------------------------------------------------------------
    // ANALYTICS AGGREGATION QUERIES
    // -----------------------------------------------------------------

    /** Total number of jobs across all subreddits. */
    long count();

    /** Count of jobs by subreddit. */
    @Query("SELECT j.subreddit, COUNT(j) FROM CrawlerJob j GROUP BY j.subreddit " +
           "ORDER BY COUNT(j) DESC")
    List<Object[]> countJobsBySubreddit();

    /** Number of jobs per status (for dashboard summary). */
    @Query("SELECT j.status, COUNT(j) FROM CrawlerJob j GROUP BY j.status")
    List<Object[]> countJobsByStatus();

    /** Jobs completed in a date range — trend analysis for activity volume. */
    @Query("SELECT j.subreddit, COUNT(j) FROM CrawlerJob j " +
           "WHERE j.completedAt BETWEEN :start AND :end " +
           "GROUP BY j.subreddit ORDER BY COUNT(j) DESC")
    List<Object[]> activeSubredditsInDateRange(
            @Param("start") Instant start,
            @Param("end") Instant end);

    /** Total successful vs failed crawl jobs. */
    @Query("SELECT CASE WHEN j.status = 'COMPLETED' THEN 1 ELSE 0 END, COUNT(*) " +
           "FROM CrawlerJob j GROUP BY CASE WHEN j.status = 'COMPLETED' THEN 1 ELSE 0 END")
    List<Object[]> successFailureCounts();

    /** Average job duration for completed jobs grouped by subreddit. */
    @Query(value = "SELECT subreddit, COALESCE(AVG(EXTRACT(EPOCH FROM (completed_at - started_at))), 0) FROM crawler_jobs WHERE status = 'COMPLETED' AND started_at IS NOT NULL AND completed_at IS NOT NULL GROUP BY subreddit ORDER BY subreddit", 
           nativeQuery = true)
    List<Object[]> avgJobDurationBySubreddit();

    /** Jobs that ran within last N hours — used for freshness checks. */
    @Query("SELECT j FROM CrawlerJob j WHERE j.completedAt > :since ORDER BY j.completedAt DESC")
    List<CrawlerJob> findRecentJobs(@Param("since") Instant since);

    /** Count of completed jobs for a specific subreddit (used in breakdown). */
    long countByStatusAndSubreddit(String status, String subreddit);

    /** All known subreddits that have been crawled. */
    @Query("SELECT DISTINCT j.subreddit FROM CrawlerJob j")
    Set<String> findAllCrawledSubreddits();

    // -----------------------------------------------------------------
    // MAINTENANCE QUERIES
    // -----------------------------------------------------------------

    /** Delete completed jobs older than cutoff for storage cleanup. */
    @Query("DELETE FROM CrawlerJob j WHERE j.status = 'COMPLETED' AND j.completedAt < :cutoff")
    long deleteOldCompletedJobs(@Param("cutoff") Instant cutoff);

    /** Jobs still in RUNNING status longer than a threshold (stale job detection). */
    @Query("SELECT j FROM CrawlerJob j WHERE j.status = 'RUNNING' " +
           "AND j.startedAt < :threshold ORDER BY j.startedAt ASC")
    List<CrawlerJob> findStaleJobs(@Param("threshold") Instant threshold);
}
