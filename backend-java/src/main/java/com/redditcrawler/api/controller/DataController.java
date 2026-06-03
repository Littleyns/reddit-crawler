package com.redditcrawler.api.controller;

import com.redditcrawler.api.service.RedditCrawlerService;
import com.redditcrawler.api.service.RedditCrawlerService.PostDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/data")
public class DataController {
    private final RedditCrawlerService crawlerService;

    public DataController(RedditCrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @GetMapping("/posts")
    public ResponseEntity<Map<String, Object>> getPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int pageSize,
            @RequestParam(required = false) String subreddit,
            @RequestParam(required = false) String search) {

        List<PostDTO> allPosts = new ArrayList<>();
        
        for (var job : crawlerService.getAllJobs()) {
            Object statusObj = job.get("status");
            if (!"COMPLETED".equals(String.valueOf(statusObj))) continue;
            Object obj = job.get("resultsJson");
            if (!(obj instanceof List<?> list)) continue;
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultsList = (List<Map<String, Object>>) obj;
            for (Map<String, Object> row : resultsList) {
                PostDTO dto = new PostDTO();
                dto.title = asString(row.get("title"));
                dto.body = asString(row.get("body"));
                dto.author = asString(row.get("author"));
                Object upsObj = row.get("upvotes");
                dto.upvotes = upsObj instanceof Number n ? n.intValue() : 0;
                Object commentsObj = row.get("commentsCount");
                dto.commentsCount = commentsObj instanceof Number n ? n.intValue() : 0;
                Object createdObj = row.get("createdUtc");
                if (createdObj instanceof Number num) {
                    dto.createdUtc = java.time.Instant.ofEpochSecond(num.longValue());
                }
                dto.permalink = asString(row.get("permalink"));
                dto.subreddit = asString(row.get("subreddit"));
                allPosts.add(dto);
            }
        }

        List<PostDTO> filtered = allPosts;
        if (subreddit != null && !subreddit.isBlank()) {
            final String sr = subreddit.toLowerCase();
            filtered = filtered.stream()
                    .filter(p -> p.subreddit != null && p.subreddit.equalsIgnoreCase(sr))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.isBlank()) {
            final String term = search.toLowerCase();
            filtered = filtered.stream()
                    .filter(p -> (p.title == null || !p.title.toLowerCase().contains(term)) == false
                            || (p.body != null && p.body.toLowerCase().contains(term))
                            || (p.author != null && p.author.toLowerCase().contains(term)))
                    .collect(Collectors.toList());
        }

        int total = filtered.size();
        int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
        int fromIndex = Math.min((page - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<PostDTO> pageSlice = fromIndex < total ? filtered.subList(fromIndex, toIndex) : List.of();

        AtomicInteger idSeq = new AtomicInteger(0);
        List<Map<String, Object>> items = pageSlice.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(Math.abs(idSeq.incrementAndGet())));
            m.put("subreddit", p.subreddit != null ? p.subreddit : "");
            m.put("title", p.title != null ? p.title : "");
            m.put("author", p.author != null ? p.author : "[deleted]");
            m.put("score", p.upvotes);
            m.put("commentsCount", p.commentsCount);
            m.put("createdAt", p.createdUtc != null ? p.createdUtc.toString() : "");
            m.put("url", p.permalink != null ? p.permalink : "");
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("total", total);
        response.put("totalPages", totalPages);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/comments")
    public ResponseEntity<Map<String, Object>> getComments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int pageSize,
            @RequestParam(required = false) String subreddit,
            @RequestParam(required = false) String search) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", List.<Map<String, Object>>of());
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("total", 0);
        response.put("totalPages", 1);
        return ResponseEntity.ok(response);
    }

    private String asString(Object o) {
        return o != null ? String.valueOf(o) : "";
    }
}
