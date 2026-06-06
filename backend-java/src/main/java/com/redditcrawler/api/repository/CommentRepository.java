package com.redditcrawler.api.repository;

import com.redditcrawler.api.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for crawling Comment entities persisted in PostgreSQL.
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** Comments belonging to a specific post. */
    Page<Comment> findByPostId(Long postId, Pageable pageable);

    /** All comments from a given subreddit (joined via post). */
    @Query("SELECT c FROM Comment c JOIN c.post p WHERE p.subreddit = :subreddit")
    Page<Comment> findBySubreddit(@Param("subreddit") String subreddit, Pageable pageable);

    /** Count of total comments per subreddit. */
    @Query("SELECT p.subreddit, COUNT(c) FROM Comment c JOIN c.post p GROUP BY p.subreddit ORDER BY COUNT(c) DESC")
    java.util.List<Object[]> countCommentsBySubreddit();

    /** Delete all comments (bulk cleanup). */
    void deleteAll();
}
