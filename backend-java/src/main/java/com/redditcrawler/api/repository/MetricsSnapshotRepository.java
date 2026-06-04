package com.redditcrawler.api.repository;

import com.redditcrawler.api.entity.MetricsSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JPA repository for MetricsSnapshot entities.
 */
@Repository
public interface MetricsSnapshotRepository extends JpaRepository<MetricsSnapshot, Long> {

    List<MetricsSnapshot> findBySubredditOrderByCreatedAtDesc(String subreddit);

    Page<MetricsSnapshot> findBySnapshotType(String snapshotType, Pageable pageable);

    Page<MetricsSnapshot> findBySubredditAndSnapshotType(String subreddit, String snapshotType,
                                                         Pageable pageable);

    @Query("SELECT DISTINCT s.subreddit FROM MetricsSnapshot s WHERE s.subreddit IS NOT NULL")
    Set<String> findDistinctSubreddits();

    @Query("SELECT MAX(s.createdAt) FROM MetricsSnapshot s")
    Optional<LocalDateTime> findLatestTimestamp();

    @Query("SELECT s FROM MetricsSnapshot s " +
           "WHERE s.createdAt BETWEEN :start AND :end " +
           "ORDER BY s.createdAt ASC")
    List<MetricsSnapshot> findByTimeRange(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    @Query("SELECT s FROM MetricsSnapshot s WHERE s.snapshotType = :type " +
           "AND (:subreddit IS NULL OR s.subreddit = :subreddit) " +
           "ORDER BY s.createdAt DESC")
    List<MetricsSnapshot> latestByType(@Param("type") String type,
                                       @Param("subreddit") String subreddit);

    long countBySubredditAndCreatedAtAfter(String subreddit, LocalDateTime since);

    void deleteByCreatedAtBefore(LocalDateTime before);
}
