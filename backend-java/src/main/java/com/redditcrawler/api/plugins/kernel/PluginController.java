package com.redditcrawler.api.plugins.kernel;

import com.redditcrawler.api.plugins.interfaces.ICrawlerPlugin;
import com.redditcrawler.api.plugins.interfaces.IAnalyticsPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** REST API controller to expose and manage the Micro-Kernel plugin system. */
@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    private static final Logger log = LoggerFactory.getLogger(PluginController.class);

    @Autowired private PluginRegistry registry;

    @GetMapping("/connectors")
    public ResponseEntity<List<Map<String, Object>>> listConnectors() {
        log.info("[PluginAPI] Listing connectors");
        return ResponseEntity.ok(registry.getAvailableConnectors().stream()
            .map(c -> Map.<String, Object>of("name", c.getName(), "type", "CONNECTOR"))
            .toList());
    }

    @GetMapping("/pipelines")
    public ResponseEntity<List<Map<String, Object>>> listPipelines() {
        log.info("[PluginAPI] Listing analytics pipelines");
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/crawl")
    public ResponseEntity<Map<String, Object>> runCrawl(@RequestParam(defaultValue = "") String subredditQuery) {
        log.info("[PluginAPI] Crawling requested for: {}", subredditQuery);
        var results = registry.runCrawls(subredditQuery.isEmpty() ? "all" : subredditQuery);
        return ResponseEntity.ok(Map.of("status", "completed", "results", results));
    }

    @PostMapping("/analyze")
    public ResponseEntity<List<Map<String, Object>>> runAnalysis(@RequestBody(required = false) Map<?, ?> payload) {
        log.info("[PluginAPI] Analytics requested");
        List<ICrawlerPlugin.RawData> rawData = new ArrayList<>();
        if (payload != null && payload.containsKey("data")) {
            Object dataObj = payload.get("data");
            if (dataObj instanceof Iterable<?> items) {
                for (Object item : items) {
                    ICrawlerPlugin.RawData r = extractRawData(item);
                    if (r != null) rawData.add(r);
                }
            }
        }
        return ResponseEntity.ok(registry.runAnalysisPipelines(rawData));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> systemStatus() {
        log.info("[PluginAPI] System status requested");
        var connectors = registry.getAvailableConnectors();
        return ResponseEntity.ok(Map.of("kernel", "active", "connectors", connectors.size(), "pipelines", 0));
    }

    private static ICrawlerPlugin.RawData extractRawData(Object item) {
        if (item == null || !(item instanceof java.util.Map<?, ?> map)) return null;
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) (Object)map;
        String id = String.valueOf(m.getOrDefault("id", ""));
        String title = String.valueOf(m.getOrDefault("title", ""));
        Object ce = m.get("contentBody");
        String content = ce != null ? String.valueOf(ce) : "";
        if (content.isEmpty()) { Object cc = m.get("content"); if (cc != null) content = String.valueOf(cc); }
        Object ss = m.get("source");
        String source = ss != null ? String.valueOf(ss) : "unknown";
        try { return new ICrawlerPlugin.RawData(id, title, content, source); }
        catch (Exception e) { log.error("[PluginAPI] Failed to construct RawData: {}", e.getMessage()); return null; }
    }
}
