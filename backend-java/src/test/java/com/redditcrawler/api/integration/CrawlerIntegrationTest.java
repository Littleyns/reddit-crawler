package com.redditcrawler.api.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.redditcrawler.api.controller.CrawlerController;
import com.redditcrawler.api.controller.HealthController;
import com.redditcrawler.api.service.AsyncCrawlerRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P3-1: WebMvc integration tests for key controllers.
 * Uses @WebMvcTest to slice the MVC layer, mocks AsyncCrawlerRunner via @MockBean.
 */
@WebMvcTest({CrawlerController.class, HealthController.class})
class CrawlerIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AsyncCrawlerRunner asyncCrawlerRunner;

    void stubStart() { doNothing().when(asyncCrawlerRunner).startCrawlAsync(anyString(), any()); }

    @Test @DisplayName("POST /api/crawler/start returns 200 OK")
    void startCrawler_acceptsValidRequest() throws Exception {
        stubStart();
        String payload = "{\"subreddit\":\"test\",\"depth\":2,\"limit\":50}";
        mockMvc.perform(post("/api/crawler/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test @DisplayName("GET /api/crawler/status returns 200 JSON")
    void getStatusSummary_returnsJson() throws Exception {
        mockMvc.perform(get("/api/crawler/status"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/crawler/status/999 returns 4xx")
    void getStatusById_unknownJob() throws Exception {
        // The controller handles unknown IDs by calling service methods.
        // In a real Spring context the service might return an error or the
        // controller catches it — here we just verify no exception escapes silently.
        mockMvc.perform(get("/api/crawler/status/999"))
                .andExpect(status().is4xxClientError());
    }

    @Test @DisplayName("GET /api/health returns UP with timestamp")
    void healthCheck_returnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test @DisplayName("GET /api/crawler/jobs returns 200")
    void getJobs_returnsArray() throws Exception {
        mockMvc.perform(get("/api/crawler/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test @DisplayName("POST /api/crawler/stop returns 200")
    void stopCrawler_returnsOk() throws Exception {
        stubStart();
        mockMvc.perform(post("/api/crawler/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }
}
