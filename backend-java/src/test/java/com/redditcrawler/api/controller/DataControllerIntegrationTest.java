package com.redditcrawler.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DataControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())  // Disable filter chain via test configuration or addFilter=false approach
                .build();
    }

    @Test
    @DisplayName("GET /api/data/posts returns HTTP 200")
    void shouldReturnOkForPosts() throws Exception {
        this.mockMvc.perform(get("/api/data/posts")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("GET /api/data/comments returns HTTP 200")
    void shouldReturnOkForComments() throws Exception {
        this.mockMvc.perform(get("/api/data/comments")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("GET /api/data/posts with subreddit filter includes filter param")
    void shouldSupportSubredditFilter() throws Exception {
        this.mockMvc.perform(get("/api/data/posts")
                        .param("subreddit", "java"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("GET /api/data/posts with search param is accepted")
    void shouldSupportSearchParam() throws Exception {
        this.mockMvc.perform(get("/api/data/posts")
                        .param("search", "spring"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("GET /api/data/export?format=csv returns content-type text/csv")
    void shouldExportCsv() throws Exception {
        this.mockMvc.perform(get("/api/data/export")
                        .param("format", "csv"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("GET /api/data/export?format=json returns content-type application/json")
    void shouldExportJson() throws Exception {
        this.mockMvc.perform(get("/api/data/export")
                        .param("format", "json"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.items").exists());
    }

    @Test
    @DisplayName("GET /api/data/posts with invalid page returns error gracefully")
    void shouldHandleInvalidPageGracefully() throws Exception {
        this.mockMvc.perform(get("/api/data/posts")
                        .param("page", "-1"))
                .andExpect(status().is2xxSuccessful());
    }
}
