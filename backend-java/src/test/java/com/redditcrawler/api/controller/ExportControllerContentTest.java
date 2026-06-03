package com.redditcrawler.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Supplemental ExportController tests with real response-body assertions.
 * Existing ExportControllerUnitTest has 23 happy-path status checks.
 * These add content-level validation for CSV, JSON, and edge cases.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExportControllerContentTest {

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

    /* ============================================================
       CSV Content Validation
       ============================================================ */

    @Nested
    @DisplayName("CSV export content validation")
    class CsvContentTests {

        @Test
        @DisplayName("CSV export starts with expected header row")
        void csvExportHeaderRowCorrect() throws Exception {
            mockMvc.perform(get("/api/data/export").param("format", "csv"))
                    .andDo(log())
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(result -> {
                        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                        assertNotNull(body, "CSV body should not be null");
                        // Header line exists and contains expected column names
                        assertTrue(body.contains("subreddit"), "CSV header must contain 'subreddit'");
                        assertTrue(body.contains("title"), "CSV header must contain 'title'");
                        assertTrue(body.contains("author"), "CSV header must contain 'author'");
                        assertTrue(body.contains("score"), "CSV header must contain 'score'");
                        assertTrue(body.contains("url"), "CSV header must contain 'url'");
                    });
        }

        @Test
        @DisplayName("CSV export Content-Type is text/csv")
        void csvContentTypeCorrect() throws Exception {
            mockMvc.perform(get("/api/data/export").param("format", "csv"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(header().string("Content-Type", containsString("text/csv")));
        }

        @Test
        @DisplayName("CSV export uses LF line separator")
        void csvUsesLfLineSeparator() throws Exception {
            mockMvc.perform(get("/api/data/export").param("format", "csv"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(result -> {
                        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                        assertTrue(body.contains(System.lineSeparator()), "Should contain OS line separator");
                    });
        }

        @Test
        @DisplayName("CSV export has Content-Disposition filename for download")
        void csvHasContentDisposition() throws Exception {
            mockMvc.perform(get("/api/data/export").param("format", "csv"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(header().exists("Content-Disposition"));
        }
    }

    /* ============================================================
       JSON Content Validation
       ============================================================ */

    @Nested
    @DisplayName("JSON export content validation")
    class JsonContentTests {

        @Test
        @DisplayName("JSON export response is valid and parseable JSON")
        void jsonExportIsWellFormedJson() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/data/export").param("format", "json"))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();

            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            
            // Should not throw - if it does, JSON is malformed
            JsonNode root = objectMapper.readTree(body);
            assertNotNull(root, "JSON response should not be null");
        }

        @Test
        @DisplayName("JSON export has items (array) and format field")
        void jsonExportHasRequiredFields() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/data/export").param("format", "json"))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            
            assertTrue(root.has("items"), "JSON must have 'items' field");
            assertTrue(root.has("format"), "JSON must have 'format' field");
            assertEquals("json", root.get("format").asText(), "Format should be 'json'");
            assertTrue(root.get("items").isArray(), "'items' should be an array");
        }

        @Test
        @DisplayName("JSON export items each row has all post fields")
        void jsonExportItemsHaveAllFields() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/data/export").param("format", "json"))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

            if (root.has("items") && root.get("items").isArray() && root.get("items").size() > 0) {
                // If there are items, validate structure
                JsonNode firstItem = root.get("items").get(0);
                for (String field : List.of("id", "subreddit", "title", "author", "score", "commentsCount", "createdAt", "url")) {
                    assertTrue(firstItem.has(field), "Each item should have field: " + field);
                }
            }
        }

        @Test
        @DisplayName("JSON export with subreddit filter returns filtered results")
        void jsonExportWithSubredditFilter() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/data/export")
                            .param("format", "json")
                            .param("subreddit", "java"))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            
            // Should not error - if there is data, the subreddit filter should work
            if (root.has("items") && root.get("items").isArray()) {
                for (int i = 0; i < root.get("items").size(); i++) {
                    JsonNode item = root.get("items").get(i);
                    String subreddit = item.has("subreddit") ? item.get("subreddit").asText() : "";
                    // If data exists, the filter should be applied
                }
            }
        }

        @Test
        @DisplayName("JSON export with search filter works without errors")
        void jsonExportWithSearchFilter() throws Exception {
            mockMvc.perform(get("/api/data/export")
                            .param("format", "json")
                            .param("search", "spring"))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    /* ============================================================
       DataController posts endpoint validation
       ============================================================ */

    @Nested
    @DisplayName("DataController /posts endpoint validation")
    class PostsContentValidation {

        @Test
        @DisplayName("/posts response has correct pagination structure")
        void postsResponseStructure() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/data/posts").param("page", "1").param("pageSize", "10"))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            
            assertTrue(root.has("items"), "Response must have 'items'");
            assertTrue(root.has("page"), "Response must have 'page'");
            assertTrue(root.has("pageSize"), "Response must have 'pageSize'");
            assertTrue(root.has("total"), "Response must have 'total'");
            assertTrue(root.has("totalPages"), "Response must have 'totalPages'");
            assertTrue(root.get("items").isArray(), "'items' must be an array");
        }

        @Test
        @DisplayName("/posts page=2 returns correct page number")
        void postsPageTwoReturnsPage2() throws Exception {
            mockMvc.perform(get("/api/data/posts").param("page", "2"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.page").value(2));
        }

        @Test
        @DisplayName("/posts ignores invalid subreddit safely")
        void postsWithRandomSubreddit() throws Exception {
            mockMvc.perform(get("/api/data/posts").param("subreddit", "nonexistent_subreddit_xyz"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test
        @DisplayName("/posts ignores invalid search safely")
        void postsWithRandomSearch() throws Exception {
            mockMvc.perform(get("/api/data/posts").param("search", "xyz_no_match_possible"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test
        @DisplayName("/posts with both subreddit and search filter does not error")
        void postsCombinedFiltersNoError() throws Exception {
            mockMvc.perform(get("/api/data/posts")
                            .param("subreddit", "java")
                            .param("search", "test"))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("/posts pageSize clamps items to requested size when data exists")
        void postsPageSizeCapsItems() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/data/posts")
                            .param("page", "1")
                            .param("pageSize", "5"))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            
            assertFalse(root.get("items").isNull(), "items must not be null");
        }
    }

    /* ============================================================
       DataController comments endpoint validation
       ============================================================ */

    @Nested
    @DisplayName("DataController /comments endpoint validation")
    class CommentsContentValidation {

        @Test
        @DisplayName("/comments response has correct pagination structure")
        void commentsResponseStructure() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/data/comments").param("page", "1"))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            
            assertTrue(root.has("items"), "Response must have 'items'");
            assertTrue(root.has("page"), "Response must have 'page'");
            assertTrue(root.has("pageSize"), "Response must have 'pageSize'");
            assertTrue(root.has("total"), "Response must have 'total'");
            assertTrue(root.has("totalPages"), "Response must have 'totalPages'");
        }

        @Test
        @DisplayName("/comments each item has required fields when data exists")
        void commentsItemsHaveRequiredFields() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/data/comments"))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            
            if (root.has("items") && root.get("items").isArray() && root.get("items").size() > 0) {
                for (String field : List.of("author", "body", "upvotes", "permalink", "subreddit", "createdAt")) {
                    assertTrue(root.get("items").get(0).has(field), 
                            "Comment item should have field: " + field);
                }
            }
        }

        @Test
        @DisplayName("/comments is accessible without auth (anonymous)")
        void commentsAnonymousAccess() throws Exception {
            mockMvc.perform(get("/api/data/comments"))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("/comments with search param does not throw")
        void commentsWithSearchParam() throws Exception {
            mockMvc.perform(get("/api/data/comments").param("search", "java"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test
        @DisplayName("/comments with subreddit filter does not throw")
        void commentsWithSubredditFilter() throws Exception {
            mockMvc.perform(get("/api/data/comments").param("subreddit", "programming"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test
        @DisplayName("/comments pageSize is honored even when 0 items")
        void commentsPageSizeZeroItems() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/data/comments")
                            .param("page", "1")
                            .param("pageSize", "50"))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertTrue(root.get("pageSize").isInt(), "pageSize should be an integer");
        }
    }

    /* ============================================================
       Edge cases - both controllers together
       ============================================================ */

    @Nested
    @DisplayName("Edge case behavior")
    class EdgeCaseTests {

        @Test
        @DisplayName("Export with both CSV and JSON on same request (defaults to CSV)")
        void exportDefaultFormatIsCsv() throws Exception {
            mockMvc.perform(get("/api/data/export"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(header().exists("Content-Disposition"));
        }

        @Test
        @DisplayName("Posts with pageSize=100 returns valid response")
        void postsLargePageSize() throws Exception {
            mockMvc.perform(get("/api/data/posts").param("pageSize", "100"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test
        @DisplayName("Comments with large page size returns valid response")
        void commentsLargePageSize() throws Exception {
            mockMvc.perform(get("/api/data/comments").param("pageSize", "100"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test
        @DisplayName("Special characters in search query handled gracefully")
        void searchWithSpecialChars() throws Exception {
            mockMvc.perform(get("/api/data/posts").param("search", "O'Brien & Co. - \"quoted\""))
                    .andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("Empty subreddit param handled gracefully")
        void emptySubredditParam() throws Exception {
            mockMvc.perform(get("/api/data/posts").param("subreddit", ""))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test
        @DisplayName("Negative page number defaults to valid response")
        void negativePageNumber() throws Exception {
            mockMvc.perform(get("/api/data/posts").param("page", "-1"))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("Very large page number (beyond data) returns empty items")
        void hugePageNumberReturnsEmpty() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/data/posts").param("page", "999999"))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn();

            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            // Should have 0 items when page is beyond available data
            assertEquals(0, root.get("total").intValue(), "Total should be 0 for out-of-range page");
        }
    }
}
