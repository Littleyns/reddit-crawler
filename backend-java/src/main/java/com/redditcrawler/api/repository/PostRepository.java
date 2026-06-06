package com.redditcrawler.api.repository;

import com.redditcrawler.api.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JPA repository for crawled Reddit Post entities.
 * Provides standard CRUD plus analytics aggregation queries
 * over the PostgreSQL-backed posts table.
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // -----------------------------------------------------------------
    // BASIC QUERIES
    // -----------------------------------------------------------------

    /** Find all posts from a specific subreddit. */
    List<Post> findBySubreddit(@Param("subreddit") String subreddit);

    /** Find posts by author across all subreddits. */
    List<Post> findByAuthor(@Param("author") String author);

    /** Find posts with upvotes above threshold, ordered descending. */
    List<Post> findByUpvotesGreaterThanEqualOrderByUpvotesDesc(Integer minUpvotes);

    /** Find the oldest and newest post timestamps for a given sub. */
    @Query("SELECT MIN(p.createdUtc), MAX(p.createdUtc) FROM Post p WHERE p.subreddit = :sub")
    Optional<Instant[]> findDateRangeBySubreddit(@Param("sub") String sub);

    // -----------------------------------------------------------------
    // PAGINATION FOR DASHBOARD / LIST VIEWS
    // -----------------------------------------------------------------

    /** Paginated posts by subreddit with optional search filter. */
    @Query(value = "SELECT p FROM Post p WHERE (:subreddit IS NULL OR p.subreddit = :subreddit) " +
           "AND (:search IS NULL OR (p.title ILIKE CONCAT('%', :search, '%')) " +
           "OR (p.body ILIKE CONCAT('%', :search, '%'))) " +
           "ORDER BY p.upvotes DESC", nativeQuery = true)
    Page<Post> findBySubredditOrSearch(
            @Param("subreddit") String subreddit,
            @Param("search") String search,
            Pageable pageable);

    /** Paginated posts ordered by date descending. */
    @Query("SELECT p FROM Post p WHERE (:subreddit IS NULL OR p.subreddit = :subreddit) " +
           "ORDER BY p.createdUtc DESC")
    Page<Post> findBySubredditOrderedByDate(
            @Param("subreddit") String subreddit,
            Pageable pageable);

    // -----------------------------------------------------------------
    // ANALYTICS AGGREGATION QUERIES
    // -----------------------------------------------------------------

    /** Total post count (optionally filtered by subreddit). */
    long countBySubreddit(String subreddit);

    @Query("SELECT p.subreddit, COUNT(p) FROM Post p GROUP BY p.subreddit " +
           "ORDER BY COUNT(p) DESC")
    List<Object[]> countPostsBySubreddit();

    /** Average upvotes per post for a subreddit. */
    @Query("SELECT AVG(p.upvotes) FROM Post p WHERE p.subreddit = :subreddit")
    Double avgUpvotesBySubreddit(@Param("subreddit") String subreddit);

    /** Average comment count per post for a subreddit. */
    @Query("SELECT AVG(p.commentsCount) FROM Post p WHERE p.subreddit = :subreddit")
    Double avgCommentsBySubreddit(@Param("subreddit") String subreddit);

    /** Total upvotes across all posts (with optional sub filter). */
    @Query("SELECT COALESCE(SUM(p.upvotes), 0) FROM Post p " +
           "WHERE (:subreddit IS NULL OR p.subreddit = :subreddit)")
    Integer totalUpvotesBySubreddit(@Param("subreddit") String subreddit);

    /** Top posters by post count and total upvotes. */
    @Query("SELECT p.author, COUNT(p) AS postCount, SUM(COALESCE(p.upvotes, 0)) AS totalUps " +
           "FROM Post p GROUP BY p.author ORDER BY postCount DESC")
    List<Object[]> topPosters(@Param("limit") int limit);

    /** Get a set of all known subreddit names. */
    @Query("SELECT DISTINCT p.subreddit FROM Post p")
    Set<String> findAllSubreddits();

    /** Posts created within a time range — useful for trend analysis. */
    @Query("SELECT p FROM Post p WHERE p.createdUtc BETWEEN :start AND :end " +
           "ORDER BY p.createdUtc DESC")
    List<Post> findByDateRange(@Param("start") Instant start, @Param("end") Instant end);

    /** Posts matching multiple subreddits. */
    @Query("SELECT p FROM Post p WHERE p.subreddit IN :subreddits ORDER BY p.upvotes DESC")
    List<Post> findBySubredditsIn(@Param("subreddits") Set<String> subreddits);

    // -----------------------------------------------------------------
    // DELETION / MAINTENANCE QUERIES
    // -----------------------------------------------------------------

    /** Delete posts older than a cutoff date (cleanup). */
    @Query("DELETE FROM Post p WHERE p.createdUtc < :cutoff")
    long deletePostsOlderThan(@Param("cutoff") Instant cutoff);

    /** Posts with low engagement (upvotes < threshold). */
    @Query("SELECT p FROM Post p WHERE p.upvotes < :threshold ORDER BY p.upvotes ASC, p.createdUtc ASC")
    List<Post> findLowEngagementPosts(@Param("threshold") Integer threshold);
}
