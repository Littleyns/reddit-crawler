package com.arabtooling.redditcrawler.repository;

import com.arabtooling.redditcrawler.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByRedditId(String redditId, Pageable pageable);
    Page<Post> findByScrapingSessionId(Long sessionId, Pageable pageable);
    Page<Post> findBySubreddit(String subreddit, Pageable pageable);
    
    @Query("SELECT p FROM Post p WHERE p.scrapingSessionId = :sessionId ORDER BY p.createdAt DESC")
    Page<Post> findBySessionId(@Param("sessionId") Long sessionId, Pageable pageable);
    
    Page<Post> findByTitleContainingOrContentContaining(String titleQuery, String contentQuery, Pageable pageable);
}
