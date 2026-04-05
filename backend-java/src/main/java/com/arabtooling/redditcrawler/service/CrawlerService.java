package com.arabtooling.redditcrawler.service;

import com.arabtooling.redditcrawler.dto.CreateCrawlerSessionRequest;
import com.arabtooling.redditcrawler.dto.ScrapingSessionDTO;
import com.arabtooling.redditcrawler.entity.ScrapingSession;
import com.arabtooling.redditcrawler.mapper.ScrapingSessionMapper;
import com.arabtooling.redditcrawler.repository.ScrapingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final ScrapingSessionRepository sessionRepository;
    private final ScrapingSessionMapper sessionMapper;

    @Transactional
    public ScrapingSessionDTO startCrawl(CreateCrawlerSessionRequest request) {
        log.info("Starting crawl for subreddit: {}", request.getSubreddit());
        
        ScrapingSession session = new ScrapingSession();
        session.setSubreddit(request.getSubreddit());
        session.setSort(request.getSort());
        session.setPostLimit(request.getPostLimit());
        session.setDepth(request.getDepth());
        session.setIncludeComments(request.getIncludeComments());
        session.setKeywords(request.getKeywords());
        session.setStatus("running");
        
        ScrapingSession saved = sessionRepository.save(session);
        log.info("Crawl session created with ID: {}", saved.getId());
        
        // TODO: Integrate with actual crawler service (Python PRAW or Java worker)
        // This would trigger the actual scraping process
        
        return sessionMapper.toDTO(saved);
    }

    @Transactional
    public ScrapingSessionDTO stopCrawl(Long sessionId) {
        log.info("Stopping crawl session: {}", sessionId);
        
        Optional<ScrapingSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isPresent()) {
            ScrapingSession session = sessionOpt.get();
            session.setStatus("cancelled");
            session.setFinishedAt(java.time.LocalDateTime.now());
            saved = sessionRepository.save(session);
            return sessionMapper.toDTO(saved);
        }
        throw new RuntimeException("Crawl session not found: " + sessionId);
    }

    public ScrapingSessionDTO getStatus(Long sessionId) {
        Optional<ScrapingSession> sessionOpt = sessionRepository.findById(sessionId);
        return sessionOpt.map(sessionMapper::toDTO)
                .orElseThrow(() -> new RuntimeException("Crawl session not found: " + sessionId));
    }

    public List<ScrapingSessionDTO> getAllSessions() {
        return sessionRepository.findAll().stream()
                .map(sessionMapper::toDTO)
                .collect(Collectors.toList());
    }

    public List<ScrapingSessionDTO> getSessionsBySubreddit(String subreddit) {
        return sessionRepository.findBySubreddit(subreddit).stream()
                .map(sessionMapper::toDTO)
                .collect(Collectors.toList());
    }

    public List<ScrapingSessionDTO> getRunningSessions() {
        return sessionRepository.findByStatus("running").stream()
                .map(sessionMapper::toDTO)
                .collect(Collectors.toList());
    }
}
