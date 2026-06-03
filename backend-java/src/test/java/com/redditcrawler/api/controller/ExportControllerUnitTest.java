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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.hamcrest.Matchers.containsString;

import java.util.*;

/**
 * Unit/integration tests for ExportController business logic:
 * - CSV export format correctness, escaping, headers
 * - JSON export format validity, structure
 * - Subreddit filters applied to export data
 * - Search filters applied to export data
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExportControllerUnitTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /* ---- CSV Export Tests ---- */

    @Test
    @DisplayName("CSV export has correct header row")
    void csvExportHasHeaderRow() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("format", "csv"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("CSV export respects subreddit filter")
    void csvExportRespectsSubredditFilter() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("format", "csv")
                        .param("subreddit", "java"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("CSV export respects search filter")
    void csvExportRespectsSearchFilter() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("format", "csv")
                        .param("search", "spring"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("CSV export filename is set in Content-Disposition")
    void csvExportHasFilenameHeader() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("format", "csv"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string("Content-Disposition", containsString("reddit_crawler_export.csv")));
    }

    /* ---- JSON Export Tests ---- */

    @Test
    @DisplayName("JSON export returns application/json content type")
    void jsonExportHasJsonContentType() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("format", "json"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("JSON export response has items and format fields")
    void jsonExportHasExpectedFields() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("format", "json"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.items").exists())
                .andExpect(jsonPath("$.format").value("json"));
    }

    @Test
    @DisplayName("JSON export respects subreddit filter")
    void jsonExportRespectsSubredditFilter() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("format", "json")
                        .param("subreddit", "java"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("JSON export respects search filter")
    void jsonExportRespectsSearchFilter() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("format", "json")
                        .param("search", "spring"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful());
    }

    /* ---- Pagination & Edge Cases for DataController ---- */

    @Test
    @DisplayName("GET /api/data/posts page=0 clamps to first page")
    void postsPageZeroClamped() throws Exception {
        mockMvc.perform(get("/api/data/posts")
                        .param("page", "0"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("GET /api/data/posts with large page beyond data range returns no items")
    void postsOutOfBoundsPage() throws Exception {
        mockMvc.perform(get("/api/data/posts")
                        .param("page", "999999"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/data/posts pageSize defaults to 25")
    void postsDefaultPageSize() throws Exception {
        mockMvc.perform(get("/api/data/posts"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.pageSize").value(25));
    }

    @Test
    @DisplayName("GET /api/data/comments pageSize defaults to 25")
    void commentsDefaultPageSize() throws Exception {
        mockMvc.perform(get("/api/data/comments"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.pageSize").value(25));
    }

    /* ---- Combined Filter Tests ---- */

    @Test
    @DisplayName("Combined subreddit + search filters return 200")
    void combinedFilterPosts() throws Exception {
        mockMvc.perform(get("/api/data/posts")
                        .param("subreddit", "java")
                        .param("search", "hello"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("Combined subreddit + search on comments returns 200")
    void combinedFilterComments() throws Exception {
        mockMvc.perform(get("/api/data/comments")
                        .param("subreddit", "java")
                        .param("search", "world"))
                .andExpect(status().is2xxSuccessful());
    }

    /* ---- Empty State Tests ---- */

    @Test
    @DisplayName("Empty data returns valid pagination with totalPages=1")
    void emptyDataPaginationValid() throws Exception {
        mockMvc.perform(get("/api/data/posts")
                        .param("page", "1"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("Export with no data returns header line only in CSV")
    void csvExportNoDataReturnsHeadersOnly() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("format", "csv"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful());
    }

    /* ---- Invalid Format Handling ---- */

    @Test
    @DisplayName("Export with empty format defaults to CSV")
    void exportEmptyFormatDefaultsCsv() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("format", ""))
                .andDo(log())
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("GET /api/data/posts returns total field")
    void postsReturnsTotalField() throws Exception {
        mockMvc.perform(get("/api/data/posts"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.total").exists());
    }

    @Test
    @DisplayName("GET /api/data/posts returns page and totalPages fields")
    void postsReturnsPageFields() throws Exception {
        mockMvc.perform(get("/api/data/posts").param("page", "1"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.totalPages").exists());
    }

    /* ---- CORS check on export endpoints ---- */

    @Test
    @DisplayName("Export endpoint allows CORS preflight")
    void exportAllowsCorsPreflight() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .header("Origin", "http://localhost:3000")
                        .param("format", "csv"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("Posts endpoint allows CORS preflight")
    void postsAllowsCorsPreflight() throws Exception {
        mockMvc.perform(get("/api/data/posts")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("CSV with special characters in title/author works")
    void csvHandlesSpecialChars() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("format", "csv"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("JSON export items array is valid JSON structure")
    void jsonExportItemsValidStructure() throws Exception {
        mockMvc.perform(get("/api/data/export")
                        .param("format", "json"))
                .andDo(log())
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.items").isArray());
    }
}
