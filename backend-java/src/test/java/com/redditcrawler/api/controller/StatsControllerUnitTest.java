package com.redditcrawler.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redditcrawler.api.repository.CrawlerJobRepository;
import com.redditcrawler.api.service.NicheScorer;
import com.redditcrawler.api.service.RedditCrawlerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;

/**
 * Unit tests for StatsController and ExportController endpoints.
 * Uses manual MockMvc with mocks to avoid full Spring context load.
 */
class StatsControllerUnitTest {

    @Autowired
    private ObjectMapper objectMapper;  // Injected from parent if needed, or create our own

    private MockMvc mockMvc;
    private RedditCrawlerService crawlerService;
    private DataController dataController;

    @BeforeEach
    void setUp() {
        crawlerService = org.mockito.Mockito.mock(RedditCrawlerService.class);
        dataController = org.mockito.Mockito.mock(DataController.class);
        NicheScorer nicheScorer = org.mockito.Mockito.mock(NicheScorer.class);

        StatsController statsController = new StatsController(crawlerService, nicheScorer);
        CrawlerJobRepository jobRepoMock = org.mockito.Mockito.mock(CrawlerJobRepository.class);
        when(jobRepoMock.findByStatus("COMPLETED")).thenReturn(List.of());
        ExportController exportController = new ExportController(dataController, jobRepoMock);

        this.mockMvc = MockMvcBuilders.standaloneSetup(statsController, exportController)
                .defaultResponseCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8)
                .build();
    }

    // ---- GET /api/stats tests ----

    @Test
    void getStatsWithNoJobsZeroCounts() throws Exception {
        when(crawlerService.getAllJobs()).thenReturn(List.of());
        when(crawlerService.getPendingQueueLength()).thenReturn(0L);

        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPosts").value(0))
                .andExpect(jsonPath("$.totalComments").value(0))
                .andExpect(jsonPath("$.totalSessions").value(0))
                .andExpect(jsonPath("$.activeSubreddits").value(0));
    }

    @Test
    void getStatsCountsPostsFromCompletedJobs() throws Exception {
        List<Map<String, Object>> job1Results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> post = new HashMap<>();
            post.put("title", "Test Post " + i);
            post.put("body", "body text");
            post.put("author", "testuser");
            post.put("upvotes", i * 5);
            post.put("commentsCount", i * 2);
            post.put("createdUtc", System.currentTimeMillis() / 1000);
            post.put("permalink", "/r/java/post" + i);
            post.put("subreddit", "java");
            job1Results.add(post);
        }

        Map<String, Object> job1 = new HashMap<>();
        job1.put("jobId", "job-1");
        job1.put("status", "COMPLETED");
        job1.put("subreddit", "java");
        job1.put("resultsJson", job1Results);
        job1.put("startedAt", Instant.now().toString());

        when(crawlerService.getAllJobs()).thenReturn(List.of(job1));
        when(crawlerService.getPendingQueueLength()).thenReturn(0L);

        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPosts").value(10))
                .andExpect(jsonPath("$.totalSessions").value(1))
                .andExpect(jsonPath("$.activeSubreddits").value(1));
    }

    @Test
    void getStatsIgnoresRunningJobs() throws Exception {
        Map<String, Object> runningJob = new HashMap<>();
        runningJob.put("jobId", "job-running");
        runningJob.put("status", "RUNNING");
        runningJob.put("subreddit", "java");
        runningJob.put("resultsJson", null);

        when(crawlerService.getAllJobs()).thenReturn(List.of(runningJob));
        when(crawlerService.getPendingQueueLength()).thenReturn(1L);

        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPosts").value(0))
                .andExpect(jsonPath("$.queueDepth").value(1));
    }

    @Test
    void getStatsIncludesActivities() throws Exception {
        List<Map<String, Object>> jobs = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Map<String, Object> job = new HashMap<>();
            job.put("jobId", "job-" + i);
            job.put("status", "COMPLETED");
            job.put("subreddit", "java");
            job.put("resultsJson", List.of(Map.of("title", "test")));
            job.put("startedAt", Instant.now().toString());
            jobs.add(job);
        }

        when(crawlerService.getAllJobs()).thenReturn(jobs);
        when(crawlerService.getPendingQueueLength()).thenReturn(0L);

        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities").isArray())
                .andExpect(jsonPath("$.activities.length()").value(Math.min(7, 5)));
    }

    @Test
    void getStatsReportsFailedJobAsError() throws Exception {
        Map<String, Object> failedJob = new HashMap<>();
        failedJob.put("jobId", "job-failed");
        failedJob.put("status", "FAILED_NO_DATA");
        failedJob.put("subreddit", "java");
        failedJob.put("startedAt", Instant.now().toString());

        when(crawlerService.getAllJobs()).thenReturn(List.of(failedJob));
        when(crawlerService.getPendingQueueLength()).thenReturn(0L);

        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities").isArray())
                .andExpect(jsonPath("$.activities[0].status").value("error"));
    }

    @Test
    void getStatsReportsSuccessRate() throws Exception {
        Map<String, Object> completedJob = new HashMap<>();
        completedJob.put("jobId", "completed");
        completedJob.put("status", "COMPLETED");
        completedJob.put("subreddit", "java");

        when(crawlerService.getAllJobs()).thenReturn(List.of(completedJob));
        when(crawlerService.getPendingQueueLength()).thenReturn(0L);

        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successRate").value(100));
    }

    // ---- GET /api/data/export tests (in ExportController) ----

    @Test
    void exportCsvFormat() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Object> post1 = new HashMap<>();
        post1.put("title", "Export Test Post");
        post1.put("body", "test body");
        post1.put("author", "exporter");
        post1.put("upvotes", 42);
        post1.put("commentsCount", 7);
        post1.put("createdUtc", Instant.now().getEpochSecond());
        post1.put("permalink", "/r/test/exported");
        post1.put("subreddit", "test");
        results.add(post1);

        Map<String, Object> job = new HashMap<>();
        job.put("jobId", "export-job");
        job.put("status", "COMPLETED");
        job.put("resultsJson", results);

        when(crawlerService.getAllJobs()).thenReturn(List.of(job));
        when(crawlerService.getPendingQueueLength()).thenReturn(0L);

        // Setup dataController mock to return items for export
        Map<String, Object> postsResponse = new HashMap<>();
        postsResponse.put("items", results);
        postsResponse.put("page", 1);
        postsResponse.put("pageSize", 10000);
        when(dataController.getPosts(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(postsResponse));

        mockMvc.perform(get("/api/data/export")
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andDo(result -> {
                    String body = result.getResponse().getContentAsString();
                    if (!body.contains("id,subreddit,title,author,score,commentsCount,createdAt,url")) {
                        throw new AssertionError("CSV export missing expected header row");
                    }
                    String[] lines = body.split(System.lineSeparator());
                    if (lines.length < 2) {
                        throw new AssertionError("CSV export should have at least header + 1 data line, got " + lines.length);
                    }
                });
    }

    @Test
    void exportJsonFormat() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Object> post1 = new HashMap<>();
        post1.put("title", "JSON Export Test");
        post1.put("body", "testing json output");
        post1.put("author", "jsonuser");
        post1.put("upvotes", 10);
        post1.put("commentsCount", 3);
        post1.put("createdUtc", Instant.now().getEpochSecond());
        post1.put("permalink", "/r/tex/json");
        post1.put("subreddit", "tex");
        results.add(post1);

        Map<String, Object> job = new HashMap<>();
        job.put("jobId", "json-job");
        job.put("status", "COMPLETED");
        job.put("resultsJson", results);

        when(crawlerService.getAllJobs()).thenReturn(List.of(job));
        when(crawlerService.getPendingQueueLength()).thenReturn(0L);

        Map<String, Object> postsResponse = new HashMap<>();
        postsResponse.put("items", results);
        postsResponse.put("page", 1);
        postsResponse.put("pageSize", 10000);
        when(dataController.getPosts(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(postsResponse));

        mockMvc.perform(get("/api/data/export")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("JSON Export Test"));
    }

    @Test
    void exportCsvWithSearchFilter() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Object> post1 = new HashMap<>();
        post1.put("title", "Spring Boot");
        post1.put("body", "good framework");
        post1.put("author", "springdev");
        post1.put("upvotes", 50);
        post1.put("commentsCount", 10);
        post1.put("createdUtc", Instant.now().getEpochSecond());
        post1.put("permalink", "/r/java/spring");
        post1.put("subreddit", "java");
        results.add(post1);

        Map<String, Object> job = new HashMap<>();
        job.put("jobId", "search-job");
        job.put("status", "COMPLETED");
        job.put("resultsJson", results);

        when(crawlerService.getAllJobs()).thenReturn(List.of(job));
        when(crawlerService.getPendingQueueLength()).thenReturn(0L);

        Map<String, Object> postsResponse = new HashMap<>();
        postsResponse.put("items", results);
        postsResponse.put("page", 1);
        postsResponse.put("pageSize", 10000);
        when(dataController.getPosts(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(postsResponse));

        mockMvc.perform(get("/api/data/export")
                        .param("format", "csv")
                        .param("search", "Spring"))
                .andExpect(status().isOk());
    }

    @Test
    void exportCsvWithSubredditFilter() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> post = new HashMap<>();
            post.put("title", "Post #" + i);
            post.put("body", "content");
            post.put("author", "user" + i);
            post.put("upvotes", i * 3);
            post.put("commentsCount", i);
            post.put("createdUtc", Instant.now().getEpochSecond());
            post.put("permalink", "/r/java/post" + i);
            post.put("subreddit", "java");
            results.add(post);
        }

        Map<String, Object> job = new HashMap<>();
        job.put("jobId", "filter-job");
        job.put("status", "COMPLETED");
        job.put("resultsJson", results);

        when(crawlerService.getAllJobs()).thenReturn(List.of(job));
        when(crawlerService.getPendingQueueLength()).thenReturn(0L);

        Map<String, Object> postsResponse = new HashMap<>();
        postsResponse.put("items", results);
        postsResponse.put("page", 1);
        postsResponse.put("pageSize", 10000);
        when(dataController.getPosts(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(postsResponse));

        mockMvc.perform(get("/api/data/export")
                        .param("format", "csv")
                        .param("subreddit", "java"))
                .andExpect(status().isOk());
    }

}
