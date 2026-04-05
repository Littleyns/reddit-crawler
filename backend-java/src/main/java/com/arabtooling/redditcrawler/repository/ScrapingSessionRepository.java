package com.arabtooling.redditcrawler.repository;

import com.arabtooling.redditcrawler.entity.ScrapingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ScrapingSessionRepository extends JpaRepository<ScrapingSession, Long> {
    List<ScrapingSession> findBySubreddit(String subreddit);
    List<ScrapingSession> findByStatus(String status);
}
