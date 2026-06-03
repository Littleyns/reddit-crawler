package com.redditcrawler.api.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory ConcurrentHashMap store for crawl job tracking.
 */
@Component
public class CrawlJobStore {

    private final ConcurrentHashMap<String, Map<String, Object>> jobs = new ConcurrentHashMap<>();

    public void put(String jobId, Map<String, Object> job) {
        jobs.put(jobId, job);
    }

    public Map<String, Object> get(String jobId) {
        return jobs.get(jobId);
    }

    public boolean containsKey(String jobId) {
        return jobs.containsKey(jobId);
    }

    public void updateStatus(String jobId, String status) {
        Map<String, Object> job = jobs.get(jobId);
        if (job != null) {
            job.put("status", status);
            if ("COMPLETED".equals(status)) {
                job.put("completedAt", java.time.Instant.now().toString());
            }
        }
    }

    public void updateResults(String jobId, List<Map<String, Object>> results) {
        Map<String, Object> job = jobs.get(jobId);
        if (job != null) {
            job.put("resultsJson", (Object) results);
        }
    }

    public boolean contains(String jobId) {
        return jobs.containsKey(jobId);
    }

    public List<Map<String, Object>> getAll() {
        return new ArrayList<>(jobs.values());
    }

    public int size() {
        return jobs.size();
    }
}
