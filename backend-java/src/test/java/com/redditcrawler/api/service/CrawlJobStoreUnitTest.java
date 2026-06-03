package com.redditcrawler.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CrawlJobStore service.
 * Tests the in-memory crawl job persistence layer used by all controllers.
 */
class CrawlJobStoreUnitTest {

    private CrawlJobStore store;

    @BeforeEach
    void setUp() {
        store = new CrawlJobStore();
    }

    // ---- put/get tests ----

    @Test
    @DisplayName("put and get returns the stored job")
    void putAndGetJob() {
        Map<String, Object> job = new HashMap<>();
        job.put("jobId", "test-job-1");
        job.put("subreddit", "java");
        store.put("test-job-1", job);

        Map<String, Object> retrieved = store.get("test-job-1");
        assertNotNull(retrieved);
        assertEquals("java", retrieved.get("subreddit"));
    }

    @Test
    @DisplayName("get returns null for non-existent jobId")
    void getNonExistentJobReturnsNull() {
        Map<String, Object> retrieved = store.get("non-existent");
        assertNull(retrieved);
    }

    @Test
    @DisplayName("containsKey correctly reports existence")
    void containsKeyPositiveAndNegative() {
        Map<String, Object> job = new HashMap<>();
        job.put("jobId", "test-1");
        store.put("test-1", job);

        assertTrue(store.containsKey("test-1"));
        assertFalse(store.containsKey("test-2"));
    }

    // ---- updateStatus tests ----

    @Test
    @DisplayName("updateStatus updates the job status and adds completedAt")
    void updateStatusCompletesJob() {
        Map<String, Object> job = new HashMap<>();
        job.put("jobId", "status-test");
        job.put("subreddit", "java");
        store.put("status-test", job);

        Map<String, Object> beforeUpdate = store.get("status-test");
        assertNull(beforeUpdate.get("completedAt"));

        store.updateStatus("status-test", "COMPLETED");

        Map<String, Object> updated = store.get("status-test");
        assertNotNull(updated);
        assertEquals("COMPLETED", updated.get("status"));
        assertTrue(updated.containsKey("completedAt"));
    }

    @Test
    @DisplayName("updateStatus on unknown jobId is safe (no-op)")
    void updateStatusUnknownJobIsSafe() {
        assertDoesNotThrow(() -> store.updateStatus("unknown", "COMPLETED"));
    }

    // ---- updateResults tests ----

    @Test
    @DisplayName("updateResults stores post data in the job")
    void updateResultsStoresData() {
        Map<String, Object> job = new HashMap<>();
        job.put("jobId", "results-test");
        store.put("results-test", job);

        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Object> post = new HashMap<>();
        post.put("title", "Test Post");
        post.put("author", "testuser");
        post.put("upvotes", 42);
        results.add(post);

        store.updateResults("results-test", results);

        Map<String, Object> updated = store.get("results-test");
        assertNotNull(updated);
        assertNotNull(updated.get("resultsJson"));

        assertInstanceOf(List.class, updated.get("resultsJson"));
        List<?> resultList = (List<?>) updated.get("resultsJson");
        assertEquals(1, resultList.size());
    }

    // ---- getAll/size tests ----

    @Test
    @DisplayName("getAll returns all stored jobs")
    void getAllReturnsAllJobs() {
        Map<String, Object> job1 = new HashMap<>();
        job1.put("jobId", "job-a");
        store.put("job-a", job1);

        Map<String, Object> job2 = new HashMap<>();
        job2.put("jobId", "job-b");
        store.put("job-b", job2);

        assertEquals(2, store.size());

        List<Map<String, Object>> jobs = store.getAll();
        assertEquals(2, jobs.size());

        assertTrue(jobs.stream().anyMatch(j -> j.get("jobId").equals("job-a")));
        assertTrue(jobs.stream().anyMatch(j -> j.get("jobId").equals("job-b")));
    }

    @Test
    @DisplayName("getAll returns empty list when no jobs exist")
    void getAllEmptyWhenNoJobs() {
        List<Map<String, Object>> jobs = store.getAll();
        assertNotNull(jobs);
        assertTrue(jobs.isEmpty());
    }

    @Test
    @DisplayName("size reports correct count")
    void sizeReportsCorrectCount() {
        assertEquals(0, store.size());

        store.put("s-1", Map.of("jobId", "s-1"));
        assertEquals(1, store.size());

        store.put("s-2", Map.of("jobId", "s-2"));
        store.put("s-3", Map.of("jobId", "s-3"));
        assertEquals(3, store.size());
    }

    // ---- updateComments tests ----

    @Test
    @DisplayName("updateComments stores comment data in the job")
    void updateCommentsStoresData() {
        Map<String, Object> job = new HashMap<>();
        job.put("jobId", "comments-test");
        store.put("comments-test", job);

        List<Map<String, Object>> comments = new ArrayList<>();
        Map<String, Object> c1 = new HashMap<>();
        c1.put("author", "commenter1");
        c1.put("body", "Great post!");
        comments.add(c1);

        store.updateComments("comments-test", comments);

        Map<String, Object> updated = store.get("comments-test");
        assertNotNull(updated);
        assertNotNull(updated.get("commentsJson"));

        assertInstanceOf(List.class, updated.get("commentsJson"));
        List<?> list = (List<?>) updated.get("commentsJson");
        assertEquals(1, list.size());
    }

    @Test
    @DisplayName("getAll includes jobs with both results and comments")
    void getAllIncludesJobsWithCommentsAndResults() {
        Map<String, Object> job = new HashMap<>();
        job.put("jobId", "full-job");
        job.put("subreddit", "java");
        store.put("full-job", job);

        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("title", "Test"));
        store.updateResults("full-job", results);

        List<Map<String, Object>> comments = new ArrayList<>();
        comments.add(Map.of("author", "c1", "body", "Nice"));
        store.updateComments("full-job", comments);

        List<Map<String, Object>> jobs = store.getAll();
        assertEquals(1, jobs.size());

        Map<String, Object> retrieved = jobs.get(0);
        assertNotNull(retrieved.get("resultsJson"));
        assertNotNull(retrieved.get("commentsJson"));
    }

    // ---- contains (alias for containsKey) tests ----

    @Test
    @DisplayName("contains returns true for existing jobId")
    void containsTrueWhenExists() {
        store.put("c-1", Map.of());
        assertTrue(store.contains("c-1"));
        assertFalse(store.contains("c-2"));
    }
}
