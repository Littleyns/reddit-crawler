package com.redditcrawler.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REST controller for data export endpoints (CSV/JSON download).
 */
@RestController
@RequestMapping("/api/data")
public class ExportController {

    private final DataController dataController;

    public ExportController(DataController dataController) {
        this.dataController = dataController;
    }

    // -----------------------------------------------------------------
    // GET /api/data/export?format=csv|json&subreddit=X&search=X
    // -----------------------------------------------------------------
    @GetMapping("/export")
    public ResponseEntity<String> exportData(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String subreddit,
            @RequestParam(required = false) String search) {

        // Reuse the same filtered slice, then serialize as CSV or JSON.
        // NOTE: We reuse the post query here without pagination (export all).
        Map<String, Object> postsResp = dataController.getPosts(1, 10_000, subreddit, search).getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) postsResp.get("items");

        if ("json".equalsIgnoreCase(format)) {
            String jsonBody;
            try {
                jsonBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(items);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                return ResponseEntity.internalServerError().body("{\"error\":\"serialization failed\"}");
            }
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(jsonBody);
        }

        // Default: CSV format (matching frontend expectation)
        StringBuilder csv = new StringBuilder();
        csv.append("id,subreddit,title,author,score,commentsCount,createdAt,url\n");
        for (Map<String, Object> item : items) {
            csv.append("\"")
               .append(String.valueOf(item.getOrDefault("id", "")))
               .append("\",\"")
               .append(escapeCsv(String.valueOf(item.getOrDefault("subreddit", ""))))
               .append("\",\"")
               .append(escapeCsv(String.valueOf(item.getOrDefault("title", ""))))
               .append("\",\"")
               .append(escapeCsv(String.valueOf(item.getOrDefault("author", ""))))
               .append("\",")
               .append(String.valueOf(item.getOrDefault("score", 0)))
               .append(",")
               .append(String.valueOf(item.getOrDefault("commentsCount", 0)))
               .append(",\"")
               .append(escapeCsv(String.valueOf(item.getOrDefault("createdAt", ""))))
               .append("\",\"")
               .append(escapeCsv(String.valueOf(item.getOrDefault("url", ""))))
               .append("\"")
               .append(System.lineSeparator());
        }

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"reddit_crawler_export.csv\"")
                .body(csv.toString());
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }
}
