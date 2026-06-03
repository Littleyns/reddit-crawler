package com.redditcrawler.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redditcrawler.api.security.UserService;
import com.redditcrawler.api.service.CrawlJobStore;
import com.redditcrawler.api.service.NicheScorer;
import com.redditcrawler.api.service.RedditCrawlerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest({CrawlerController.class, NicheScoreController.class})
@AutoConfigureMockMvc(addFilters = false)  // Disable Spring Security filter chain in tests
@TestPropertySource(properties = {"app.jwt.secret=test-secret-key-for-testing-only-must-be-256-bits"})
class CrawlerControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RedditCrawlerService crawlerService;

    @MockBean
    private NicheScorer nicheScorer;

    @MockBean
    private CrawlJobStore crawlJobStore;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("POST /api/crawler/start rejects missing subreddit")
    void shouldRejectMissingSubreddit() throws Exception {
        mockMvc.perform(post("/api/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/crawler/start delegates to service on valid input")
    void shouldStartCrawlOnValidInput() throws Exception {
        when(crawlerService.startCrawl(anyString(), anyMap()))
                .thenReturn("mock-job-id-123");

        String body = "{\"subreddit\": \"java\", \"limit\": 50}";

        mockMvc.perform(post("/api/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("mock-job-id-123"))
                .andExpect(jsonPath("$.subreddit").value("java"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("GET /api/crawler/status/{jobId} returns 404 for unknown job")
    void shouldReturnNotFoundForUnknownJob() throws Exception {
        when(crawlerService.getStatus(anyString())).thenReturn(null);

        mockMvc.perform(get("/api/crawler/status/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("DELETE /api/crawler/stop/{jobId} stops existing job")
    void shouldStopExistingJob() throws Exception {
        when(crawlerService.getAllJobs()).thenReturn(
                List.of(Map.of("jobId", "existing-job", "subreddit", "java"))
        );

        mockMvc.perform(delete("/api/crawler/stop/existing-job"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("DELETE /api/crawler/stop/{jobId} 404 for unknown job")
    void shouldStopUnknownJobNotFound() throws Exception {
        when(crawlerService.getAllJobs()).thenReturn(List.of());

        mockMvc.perform(delete("/api/crawler/stop/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("GET /api/niche/score uses cached crawl results if available")
    void shouldUseCachedResults() throws Exception {
        RedditCrawlerService.PostDTO post = new RedditCrawlerService.PostDTO();
        post.title = "Mock Post";
        post.body = "mock body";
        post.author = "testuser";
        post.upvotes = 10;
        post.commentsCount = 5;
        post.createdUtc = java.time.Instant.now();
        post.permalink = "/r/test/post";
        post.subreddit = "java";

        Map<String, Object> job = new java.util.LinkedHashMap<>();
        job.put("jobId", "cached-job-1");
        job.put("subreddit", "java");
        job.put("status", "COMPLETED");
        job.put("resultsJson", List.of(
                Map.of("title", "Cached Post", "body", "cached body", "author", "cacheduser",
                       "upvotes", 100, "commentsCount", 20, "createdUtc", System.currentTimeMillis()/1000,
                       "permalink", "/r/java/cached", "subreddit", "java")
        ));

        when(crawlerService.getAllJobs()).thenReturn(List.of(job));
        when(nicheScorer.score(org.mockito.Mockito.anyList())).thenReturn(
                Map.of("technical-deep", 8.5, "creative", 4.2));

        mockMvc.perform(get("/api/niche/score")
                        .param("subreddit", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("cached_crawl"));
    }
}
