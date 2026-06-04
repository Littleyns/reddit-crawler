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
import java.util.Optional;

public interface CrawlerJobRepository extends JpaRepository<CrawlerJob, Long> {

    List<CrawlerJob> findByStatus(String status);

    Page<CrawlerJob> findByStatus(String status, Pageable pageable);

    Optional<CrawlerJob> findById(Long id);

    List<CrawlerJob> findByUrlContaining(String url);

    @Query("SELECT j FROM CrawlerJob j WHERE j.updatedAt > :since")
    List<CrawlerJob> findJobsUpdatedSince(LocalDateTime since);

    long countByStatus(String status);

    /** Count of success/failure jobs using JPQL boolean test. */
    @Query("""
            SELECT CASE WHEN c.status = 'COMPLETED' THEN 1 ELSE 0 END AS succ,
                   COUNT(*) FROM CrawlerJob c GROUP BY CASE WHEN c.status = 'COMPLETED' THEN 1 ELSE 0 END
            """)
    List<Object[]> successFailureCounts();

    /** Active subreddits in a date range by recent crawl jobs. */
    @Query(value = """
            SELECT DISTINCT tj.subreddit, COUNT(*)
            FROM crawler_jobs tj
            WHERE tj.updated_at >= :start AND tj.updated_at <= :end
            GROUP BY tj.subreddit
            """, nativeQuery = true)
    List<Object[]> activeSubredditsInDateRange(@Param("start") Instant start, @Param("end") Instant end);

    /** Recent completed jobs after a given instant. */
    @Query("""
            SELECT j FROM CrawlerJob j
            WHERE j.completedAt >= :since AND j.status = 'COMPLETED'
            ORDER BY j.completedAt DESC
            """)
    List<CrawlerJob> findRecentJobs(@Param("since") Instant since);

    /** Count jobs per subreddit with completed status. */
    @Query(value = """
            SELECT subreddit, COUNT(*) FROM crawler_jobs
            WHERE status = 'COMPLETED'
            GROUP BY subreddit
            """, nativeQuery = true)
    List<Object[]> countJobsBySubreddit();

    /** Jobs finished after given instant for the breakdown helper. */
    @Query("""
            SELECT j FROM CrawlerJob j WHERE j.completedAt >= :since AND j.status = 'COMPLETED'
            ORDER BY j.jobId DESC
            """)
    List<CrawlerJob> findFinishedAfter(@Param("since") Instant since);

    /** Average crawl duration per subreddit in seconds. */
    @Query(value = """
            SELECT subreddit, AVG(
                TIMESTAMPDIFF(SECOND, started_at, completed_at)
            ) FROM crawler_jobs
            WHERE status = 'COMPLETED' AND started_at IS NOT NULL AND completed_at IS NOT NULL
            GROUP BY subreddit
            """, nativeQuery = true)
    List<Object[]> avgJobDurationBySubreddit();
}