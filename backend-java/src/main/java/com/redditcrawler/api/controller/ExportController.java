package com.redditcrawler.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import com.redditcrawler.api.dto.ExportQuery;
import com.redditcrawler.api.model.CrawlerJob;
import com.redditcrawler.api.repository.CrawlerJobRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for data export endpoints (CSV download).
 */
@RestController
@RequestMapping("/api/data/export")
public class ExportController {

    private final DataController dataController;
    private final CrawlerJobRepository crawlerJobRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExportController(DataController dataController,
                          CrawlerJobRepository crawlerJobRepository) {
        this.dataController = dataController;
        this.crawlerJobRepository = crawlerJobRepository;
    }

    // ====================================================================
    // GET /api/data/export (existing) -- CSV / JSON download with filters
    // ====================================================================

    @GetMapping("")
    public ResponseEntity<String> exportData(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String subreddit,
            @RequestParam(required = false) String search) {

        Map<String, Object> postsResp = dataController.getPosts(1, 10_000, subreddit, search).getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) postsResp.get("items");

        if ("json".equalsIgnoreCase(format)) {
            try {
                String jsonBody = objectMapper.writeValueAsString(
                        Map.of("items", items, "format", "json"));
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body(jsonBody);
            } catch (Exception e) {
                return ResponseEntity.internalServerError()
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("{\"error\":\"serialization failed\"}");
            }
        }

        StringBuilder csv = new StringBuilder();
        csv.append("id,subreddit,title,author,score,commentsCount,createdAt,url\n");
        for (Map<String, Object> item : items) {
            csv.append("\"")
               .append(escapeCsv(String.valueOf(item.getOrDefault("id", ""))))
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
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reddit_crawler_export.csv\"")
                .body(csv.toString());
    }

    // ====================================================================
    // GET /api/data/export/{crawlerJobId}/csv  (NEW)
    // Export all results from a specific crawl job as CSV.
    // Columns: title, url, subreddit, type (POST/COMMENT), sentiment,
    //          keywords, timestamp
    // ====================================================================

    @GetMapping("/{crawlerJobId}/csv")
    public ResponseEntity<String> exportJobCsv(@PathVariable("crawlerJobId") String crawlerJobId) {

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"title", "url", "subreddit", "type", "sentiment", "keywords", "timestamp"});

        // 1. Try loading from CrawlerJobRepository (JPA-backed entity).
        try {
            List<CrawlerJob> allJobs = crawlerJobRepository.findByStatus("COMPLETED");
            for (CrawlerJob job : allJobs) {
                if (!crawlerJobId.equals(job.getJobId())) {
                    continue;
                }
                buildRowsFromCrawlerJob(job, rows);
            }
        } catch (Exception e) {
            // Silently fall-through to live DataController sources on any error.
        }

        // If we already have data from CrawlerJobRepository use it directly.
        if (rows.size() > 1) {
            String csv = buildCsvWithOpenCsv(rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"reddit_crawler_export_" + crawlerJobId + ".csv\"")
                    .body(csv);
        }

        // 2. Fallback: use the existing posts/comments path via DataController.
        Map<String, Object> resp = dataController.getPosts(1, 10_000, null, null).getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) resp.get("items");

        if (items.isEmpty()) {
            rows.add(new String[]{"(none)", "", crawlerJobId, "POST", "neutral", "", ""});
        } else {
            for (Map<String, Object> item : items) {
                rows.add(mapToExportRow(item));
            }
        }

        String csv = buildCsvWithOpenCsv(rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reddit_crawler_export_" + crawlerJobId + ".csv\"")
                .body(csv);
    }

    // ====================================================================
    // POST /api/data/export/query/csv  (NEW)
    // Body: { "subreddit": "...", "startDate": "...", "endDate": "...",
    //         "type": "post|comment" }
    // Return CSV with columns: title, url, subreddit, type, sentiment,
    //                          keywords, timestamp
    // ====================================================================

    @PostMapping("/query/csv")
    public ResponseEntity<String> exportQueryCsv(@RequestBody ExportQuery query) {
        String subredditFilter = query.getSubreddit();
        String startStr      = query.getStartDate();
        String endStr        = query.getEndDate();
        String typeFilter    = query.getType() != null
                ? query.getType().toLowerCase() : "post";

        Instant startBound = parseDate(startStr);
        Instant endBound   = parseDate(endStr);

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"title", "url", "subreddit", "type", "sentiment", "keywords", "timestamp"});

        // ---- Posts ----
        if (!"comment".equals(typeFilter)) {
            String subForApi = (subredditFilter == null || subredditFilter.isEmpty())
                    ? null : subredditFilter;
            Map<String, Object> resp = dataController.getPosts(1, 10_000, subForApi, null).getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) resp.get("items");

            for (Map<String, Object> item : items) {
                if (!applyTimeFilter(asString(item.get("createdAt")), startBound, endBound)) {
                    continue;
                }
                rows.add(mapToExportRow(item));
            }
        }

        // ---- Comments ----
        if ("comment".equals(typeFilter)) {
            Map<String, Object> resp = dataController.getComments(1, 10_000,
                    subredditFilter, null).getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) resp.get("items");

            for (Map<String, Object> ci : items) {
                if (!applyTimeFilter(asString(ci.get("createdAt")), startBound, endBound)) {
                    continue;
                }
                rows.add(mapCommentToExportRow(ci));
            }
        }

        // Empty result set -> still return header-only CSV.
        if (rows.size() == 1) {
            String srTag = subredditFilter != null && !subredditFilter.isEmpty()
                    ? subredditFilter : "(none)";
            rows.add(new String[]{"(none)", "", srTag, typeFilter.toUpperCase(), "neutral", "", ""});
        }

        // Build filename.
        String tag = subredditFilter != null && !subredditFilter.isEmpty()
                ? subredditFilter.replaceAll("[^a-zA-Z0-9_]", "_") : "all";
        String filename = "reddit_crawler_" + typeFilter + "_" + tag + ".csv";

        String csv = buildCsvWithOpenCsv(rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    /**
     * Read crawl results from CrawlerJob.resultsJson and populate rows.
     */
    private void buildRowsFromCrawlerJob(CrawlerJob job, List<String[]> rows) throws Exception {
        String payload = job.getResultsJson();
        if (payload == null || payload.isEmpty()) return;

        List<Map<String, Object>> posts = objectMapper.readValue(
                payload, new TypeReference<List<Map<String, Object>>>() {});

        for (Map<String, Object> post : posts) {
            String title    = asString(post.get("title"));
            String url      = asString(post.get("permalink"));
            String sr       = job.getSubreddit();

            // Sentiment.
            String sentiment = "neutral";
            try {
                Object sObj = post.getOrDefault("sentiment", null);
                if (sObj != null) sentiment = asString(sObj).trim().toLowerCase();
            } catch (Exception ignored) {}

            // Keywords.
            String keywords = "";
            Object kwObj = post.get("keywords");
            if (kwObj instanceof java.util.List<?> list) {
                keywords = list.stream()
                        .map(k -> asString(k)).collect(Collectors.joining(", "));
            } else if (kwObj != null) {
                keywords = asString(kwObj);
            }

            // Timestamp.
            String ts = "";
            Object cv = post.get("createdUtc");
            if (cv instanceof Number num) {
                ts = Instant.ofEpochSecond(num.longValue()).toString();
            } else if (job.getCompletedAt() != null) {
                ts = job.getCompletedAt().toString();
            }

            rows.add(new String[]{title, url, sr, "POST", sentiment, keywords, ts});
        }
    }

    /** Map DataController post row -> export CSV row (7 columns). */
    private String[] mapToExportRow(Map<String, Object> item) {
        String title   = asString(item.get("title"));
        String url     = asString(item.get("url"));
        String sr      = asString(item.get("subreddit"));
        String sentiment = "neutral";

        // Try to get timestamp.
        String ts;
        try {
            Object co = item.get("createdAt");
            if (co instanceof Number n) {
                ts = Instant.ofEpochSecond(n.longValue()).toString();
            } else {
                ts = asString(co);
            }
        } catch (Exception ignored) {
            ts = "";
        }

        return new String[]{title, url, sr, "POST",
                sentiment, "", ts};
    }

    /** Map DataController comment row -> export CSV row. */
    private String[] mapCommentToExportRow(Map<String, Object> item) {
        String parentTitle = asString(item.get("parentPostTitle"));
        if (parentTitle.isEmpty()) {
            parentTitle = asString(item.get("body"));
            if (parentTitle.length() > 100) parentTitle = parentTitle.substring(0, 97) + "...";
        }

        String url      = asString(item.get("permalink"));
        String sr       = asString(item.get("subreddit"));

        String ts;
        try {
            Object co = item.get("createdAt");
            if (co instanceof Number n) {
                ts = Instant.ofEpochSecond(n.longValue()).toString();
            } else {
                ts = asString(co);
            }
        } catch (Exception ignored) {
            ts = "";
        }

        return new String[]{parentTitle, url, sr, "COMMENT",
                "neutral", "", ts};
    }

    /** Build CSV with OpenCSV. */
    private String buildCsvWithOpenCsv(List<String[]> rows) {
        StringWriter sw = new StringWriter();
        try (CSVWriter w = new CSVWriter(sw)) {
            for (String[] r : rows) {
                if (r.length < 7) r = java.util.Arrays.copyOf(r, 7);
                w.writeNext(r);
            }
        } catch (Exception e) {
            // Manual fallback.
            StringBuilder sb = new StringBuilder();
            sb.append("title,url,subreddit,type,sentiment,keywords,timestamp\n");
            for (String[] r : rows) {
                for (int i = 0; i < r.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(escapeCsv(r[i]));
                }
                sb.append(System.lineSeparator());
            }
            return sb.toString();
        }
        return sw.toString();
    }

    /** Time-range guard — returns true if a row should be kept. */
    private boolean applyTimeFilter(String tsStr, Instant startBound, Instant endBound) {
        if (startBound == null && endBound == null) return true;
        try {
            long epoch = Long.parseLong(tsStr);
            Instant ts = Instant.ofEpochSecond(epoch);
            if (startBound != null && ts.isBefore(startBound)) return false;
            if (endBound   != null && ts.isAfter(endBound)   ) return false;
        } catch (NumberFormatException ignored) {
            // unrecognised timestamp — keep anyway.
        }
        return true;
    }

    private String asString(Object o) {
        return o != null ? String.valueOf(o) : "";
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** Parse a date string: epoch integer or ISO-8601. */
    private Instant parseDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String cleaned = s.trim();

        // Epoch integer?
        try {
            long l = Long.parseLong(cleaned);
            return Instant.ofEpochSecond(l >= 1_000_000_000_000L ? l / 1000 : l);
        } catch (NumberFormatException ignored) {}

        // Plain year?
        if (cleaned.matches("\\d{4}")) {
            cleaned += "-01-01T00:00:00Z";
        } else {
            // Strip trailing colons (Instant.parse() rejects them).
            cleaned = cleaned.replaceAll(":+$", "");

            // If not ISO-8601 and not local, append Z.
            if (!cleaned.toLowerCase().contains("z") && !cleaned.contains("+")
                    && cleaned.length() > 20 && !cleaned.matches("^\\d{4}-\\d{2}.*")) {
                // Already has date prefix — try parsing with Z appended.
                cleaned = cleaned + "Z";
            }
        }

        try {
            return Instant.parse(cleaned);
        } catch (Exception ignored) {}

        return null;
    }
}
