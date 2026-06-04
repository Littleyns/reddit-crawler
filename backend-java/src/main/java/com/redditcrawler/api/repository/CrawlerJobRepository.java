package com.redditcrawler.api.repository;

import com.redditcrawler.api.model.CrawlerJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface CrawlerJobRepository extends JpaRepository<CrawlerJob, Long> {


    @Query("SELECT j FROM CrawlerJob j ORDER BY j.updatedAt DESC")
    Page<CrawlerJob> findRecent(Pageable pageable);

    @Query("SELECT j FROM CrawlerJob j WHERE j.subreddit = :subreddit")
    List<CrawlerJob> findBySubreddit(@Param("subreddit") String subreddit);

    Page<CrawlerJob> findByStatus(String status, Pageable pageable);

    Page<CrawlerJob> findByStatusIn(List<String> statuses, Pageable pageable);

    @Query("SELECT j FROM CrawlerJob j WHERE j.updatedAt > :since")
    List<CrawlerJob> findJobsUpdatedSince(LocalDateTime since);

    long countByStatus(String status);

    @Query("SELECT COALESCE(subreddit,'N/A') as subreddit, COUNT(*)::bigint as cnt "
        + "FROM crawler_jobs WHERE status IN ('COMPLETED','SUCCESS') GROUP BY subreddit")
    List<Object[]> successFailureCounts();

    @Query(value = "SELECT COALESCE(subreddit,'N/A') AS subreddit, COUNT(*) as cnt "
        + "FROM crawler_jobs WHERE created_at >= :start AND created_at <= :end "
        + "GROUP BY subreddit ORDER BY cnt DESC", nativeQuery = true)
    List<Object[]> activeSubredditsInDateRange(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT j FROM CrawlerJob j WHERE j.status = 'COMPLETED' AND j.updatedAt > :since ORDER BY j.updatedAt DESC")
    List<CrawlerJob> findRecentJobs(@Param("since") Instant since);

    @Query(value = "SELECT COALESCE(subreddit,'N/A') AS subreddit, COUNT(*) AS job_count "
        + "FROM crawler_jobs GROUP BY subreddit ORDER BY job_count DESC", nativeQuery = true)
    List<Object[]> countJobsBySubreddit();

    @Query("SELECT j FROM CrawlerJob j WHERE j.completedAt > :after")
    List<CrawlerJob> findFinishedAfter(@Param("after") Instant after);

    /* Average job duration per subreddit in seconds (PostgreSQL). */
    @Query(value = "SELECT subreddit, "
        + "COALESCE(SUM(EXTRACT(EPOCH FROM (completed_at - started_at))::bigint), 0)::numeric / NULLIF(COUNT(*), 0) as avg_duration "
        + "FROM crawler_jobs WHERE completed_at IS NOT NULL AND started_at IS NOT NULL "
        + "GROUP BY subreddit ORDER BY avg_duration DESC", nativeQuery = true)
    List<Object[]> avgJobDurationBySubreddit();

    long countByStatusAndSubreddit(String status, String subreddit);
}
