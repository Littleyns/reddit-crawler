package com.arabtooling.redditcrawler.controller;

import com.arabtooling.redditcrawler.dto.*;
import com.arabtooling.redditcrawler.service.CrawlerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/crawler")
@Tag(name = "Crawler", description = "Crawler session management endpoints")
@RequiredArgsConstructor
public class CrawlerController {

    private final CrawlerService crawlerService;

    @PostMapping("/start")
    @Operation(summary = "Start a new crawl session", description = "Initiates a Reddit crawl for the specified subreddit")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<ScrapingSessionDTO> startCrawl(@Valid @RequestBody CreateCrawlerSessionRequest request) {
        ScrapingSessionDTO session = crawlerService.startCrawl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PostMapping("/stop/{sessionId}")
    @Operation(summary = "Stop a running crawl session", description = "Cancels an active crawling session")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<ScrapingSessionDTO> stopCrawl(@PathVariable Long sessionId) {
        ScrapingSessionDTO session = crawlerService.stopCrawl(sessionId);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/status/{sessionId}")
    @Operation(summary = "Get crawl session status", description = "Returns the current status of a crawl session")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScrapingSessionDTO> getSessionStatus(@PathVariable Long sessionId) {
        ScrapingSessionDTO session = crawlerService.getStatus(sessionId);
        return ResponseEntity.ok(session);
    }

    @GetMapping
    @Operation(summary = "Get all crawl sessions", description = "Returns a list of all crawl sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ScrapingSessionDTO>> getAllSessions() {
        List<ScrapingSessionDTO> sessions = crawlerService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/subreddit/{subreddit}")
    @Operation(summary = "Get crawl sessions for a subreddit", description = "Returns all crawl sessions for a specific subreddit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ScrapingSessionDTO>> getSessionsBySubreddit(@PathVariable String subreddit) {
        List<ScrapingSessionDTO> sessions = crawlerService.getSessionsBySubreddit(subreddit);
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/running")
    @Operation(summary = "Get all running crawl sessions", description = "Returns all crawl sessions currently in 'running' status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<List<ScrapingSessionDTO>> getRunningSessions() {
        List<ScrapingSessionDTO> sessions = crawlerService.getRunningSessions();
        return ResponseEntity.ok(sessions);
    }
}
