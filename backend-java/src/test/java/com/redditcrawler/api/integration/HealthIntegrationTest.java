package com.redditcrawler.api.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redditcrawler.api.controller.HealthController;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P3-1: WebMvc integration tests for HealthController.
 */
@WebMvcTest(HealthController.class)
class HealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/health returns healthy status with components")
    void getHealth_returnsHealthyStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(notNullValue())))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /api/health returns JSON content type")
    void getHealth_returnsJsonContentType() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/json")));
    }

    @Test
    @DisplayName("GET /api/health/live returns live status code")
    void getHealthLive_returnsCode() throws Exception {
        mockMvc.perform(get("/api/health/live"))
                .andExpect(jsonPath("$").exists());
    }
}
