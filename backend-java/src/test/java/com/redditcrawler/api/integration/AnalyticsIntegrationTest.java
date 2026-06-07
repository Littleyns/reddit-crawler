package com.redditcrawler.api.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.redditcrawler.api.controller.AnalyticsRestController;
import com.redditcrawler.api.service.AnalyticsAggregationService;
import com.redditcrawler.api.repository.PostRepository;
import com.redditcrawler.api.repository.CrawlerJobRepository;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P3-1: WebMvc integration tests for AnalyticsRestController endpoints.
 */
@WebMvcTest(com.redditcrawler.api.controller.AnalyticsRestController.class)
class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsAggregationService analyticsService;

    @MockBean
    private PostRepository postRepository;

    // ----- GET /analytics/summary -----

    @Test
    @DisplayName("GET /analytics/summary returns summary stats")
    void getSummary_returnsStats() throws Exception {
        when(analyticsService.getDashboardStats())
                .thenReturn(Map.of(
                    "totalPosts", 1500,
                    "totalComments", 3200,
                    "activeSubreddits", 12,
                    "successRate", 94.5,
                    "dailyTrend", List.<Map<String, Object>>of()
                ));

        mockMvc.perform(get("/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPosts").value(1500))
                .andExpect(jsonPath("$.successRate").value(94.5));
    }

    // ----- GET /analytics/posts/top/{subreddit} -----

    @Test
    @DisplayName("GET /analytics/posts/top returns top posts")
    void getTopPosts_returnsList() throws Exception {
        when(analyticsService.getTopPosts(anyString(), anyInt()))
                .thenReturn(List.of(
                    Map.of("title", "Best Post Ever", "subreddit", "tech", "upvotes", 420)
                ));

        mockMvc.perform(get("/analytics/posts/top").param("subreddit", "tech"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Best Post Ever"));
    }

    // ----- GET /analytics/volume/{days} -----

    @Test
    @DisplayName("GET /analytics/volume returns volume data")
    void getVolume_returnsVolumeData() throws Exception {
        when(analyticsService.getPostVolumeBySubreddit(anyInt()))
                .thenReturn(Map.of(
                    "totalPosts", 850,
                    "bySubreddit", List.<Map<String, Object>>of()
                ));

        mockMvc.perform(get("/analytics/volume").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPosts").exists());
    }

    // ----- GET /anomaly-detection/all (via AnalyticsRestController) -----

    @Test
    @DisplayName("GET /anomaly-detection/all returns empty list when no anomalies")
    void getAllAnomalies_returnsEmpty() throws Exception {
        when(analyticsService.getDashboardStats())
                .thenReturn(Map.of(
                    "totalPosts", 0,
                    "totalComments", 0,
                    "activeSubreddits", 0,
                    "successRate", 100.0,
                    "dailyTrend", List.<Map<String, Object>>of()
                ));

        mockMvc.perform(get("/anomaly-detection/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ----- GET /analytics/crawls/recent -----

    @Test
    @DisplayName("GET /analytics/crawls/recent returns recent crawls")
    void getRecentCrawls_returnsList() throws Exception {
        when(analyticsService.getRecentCrawls(anyInt()))
                .thenReturn(List.of(
                    Map.of("jobId", "job-1", "status", "COMPLETED", "subreddit", "tech")
                ));

        mockMvc.perform(get("/analytics/crawls/recent").param("n", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
