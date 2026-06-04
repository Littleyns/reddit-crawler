package com.redditcrawler.api.repository;

import com.redditcrawler.api.model.CrawlerJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;

public interface CrawlerJobRepository extends JpaRepository<CrawlerJob, Long> {

    @Query("SELECT j FROM CrawlerJob j WHERE j.url LIKE %:urlPattern%")
    List<CrawlerJob> findByUrlContaining(@Param("urlPattern") String urlPattern);

    @Query("SELECT j FROM CrawlerJob j ORDER BY j.createdAt DESC")
    Page<CrawlerJob> findRecent(Pageable pageable);

    @Query("SELECT j FROM CrawlerJob j WHERE j.subreddit = :subreddit")
    List<CrawlerJob> findBySubreddit(@Param("subreddit") String subreddit);

    Page<CrawlerJob> findByStatus(String status, Pageable pageable);

    Page<CrawlerJob> findByStatusIn(List<String> statuses, Pageable pageable);

    @Query("SELECT j FROM CrawlerJob j WHERE j.updatedAt > :since")
    List<CrawlerJob> findJobsUpdatedSince(LocalDateTime since);

    long countByStatus(String status);

    /** [isCompleted(1/0), count] pairs for success rate */
    @Query(value = """
            SELECT 1 as is_completed, COUNT(*) as cnt FROM crawler_jobs WHERE status IN ('COMPLETED', 'SUCCESS')
            UNION ALL
            SELECT 0, COUNT(*) FROM crawler_jobs WHERE status = 'FAILED'
            """, nativeQuery = true)
    List<Object[]> successFailureCounts();

    /** List of subreddits with jobs updated within the given time range */
    /** Subreddit + job count pairs for recent activity */
    @Query(value = "SELECT COALESCE(subreddit, 'N/A') AS subreddit, COUNT(*) as cnt FROM crawler_jobs WHERE created_at >= :start AND created_at <= :end GROUP BY subreddit ORDER BY cnt DESC", nativeQuery = true)
    List<Object[]> activeSubredditsInDateRange(@Param("start") Instant start, @Param("end") Instant end);

    /** Recent completed jobs */
    @Query("SELECT j FROM CrawlerJob j WHERE j.status = 'COMPLETED' AND j.updatedAt > :since ORDER BY j.updatedAt DESC")
    List<CrawlerJob> findRecentJobs(@Param("since") Instant since);

    /** Count of jobs by subreddit (native query returning Object[]) */
    @Query(value = "SELECT COALESCE(subreddit, 'N/A') AS subreddit, COUNT(*) AS job_count FROM crawler_jobs GROUP BY subreddit ORDER BY job_count DESC", nativeQuery = true)
    List<Object[]> countJobsBySubreddit();

    /** Find jobs finished after a given instant */
    @Query("SELECT j FROM CrawlerJob j WHERE j.completedAt > :after")
    List<CrawlerJob> findFinishedAfter(@Param("after") Instant after);

    /** Average job duration per subreddit */
    @Query(value = """
            SELECT subreddit,
                   AVG(
                     CASE WHEN started_at IS NOT NULL AND completed_at IS NOT NULL
                     THEN TIMESTAMPDIFF(SECOND, started_at, completed_at)
                     ELSE 0 END
                   ) AS avg_duration
            FROM crawler_jobs
            GROUP BY subreddit
            """, nativeQuery = true)
    List<Object[]> avgJobDurationBySubreddit();

    /** Count jobs with a specific status for a given subreddit */
    long countByStatusAndSubreddit(String status, String subreddit);
}