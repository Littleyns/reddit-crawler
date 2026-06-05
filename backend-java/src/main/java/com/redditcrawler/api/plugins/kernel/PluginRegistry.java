package com.redditcrawler.api.plugins.kernel;

import com.redditcrawler.api.plugins.interfaces.ICrawlerPlugin;
import com.redditcrawler.api.plugins.interfaces.IAnalyticsPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Central registry for the Micro-Kernel plugin system.
 * Manages connector and analytics pipeline discovery, registration, and execution.
 */
@Slf4j
@Component
public class PluginRegistry {

    private final List<ICrawlerPlugin> connectors = new ArrayList<>();
    private final List<IAnalyticsPipeline> pipelines = new ArrayList<>();

    public void registerConnector(ICrawlerPlugin plugin) {
        connectors.add(plugin);
        log.info("[PluginRegistry] Registered connector: {} ({})", plugin.getName(), plugin.getType());
    }

    public void registerPipeline(IAnalyticsPipeline pipeline) {
        pipelines.add(pipeline);
        log.info("[PluginRegistry] Registered pipeline: {}", pipeline.getName());
    }

    public List<ICrawlerPlugin> getAvailableConnectors() {
        return List.copyOf(connectors);
    }

    public List<IAnalyticsPipeline> getAvailablePipelines() {
        return List.copyOf(pipelines);
    }

    /** Run crawlers on the given query and return results. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> runCrawls(String query) {
        log.info("[PluginRegistry] Running creaks for: {}", query);
        List<Object> results = new ArrayList<>();
        int totalProcessed = 0;
        for (ICrawlerPlugin connector : connectors) {
            try {
                // In production this would call connector.crawl(query)
                // For now return a placeholder since actual connector implementations are uncommitted
                Map<String, Object> result = Map.of(
                    "connector", connector.getName(),
                    "query", query,
                    "status", "simulated",
                    "items_found", 0
                );
                results.add(result);
                totalProcessed++;
                log.info("[PluginRegistry] Connector {} completed for query '{}'", connector.getName(), query);
            } catch (Exception e) {
                log.error("[PluginRegistry] Connector '{}' failed: {}", connector.getName(), e.getMessage());
            }
        }
        return Map.of(
            "status", "completed",
            "results", results,
            "connectors_run", totalProcessed,
            "total_items_found", 0
        );
    }

    /** Run analytics pipelines on raw data and return combined results. */
    public List<Map<String, Object>> runAnalysisPipelines(List<ICrawlerPlugin.RawData> rawData) {
        log.info("[PluginRegistry] Running {} pipeline(s) on {} items", pipelines.size(), rawData.size());
        List<Map<String, Object>> results = new ArrayList<>();
        for (IAnalyticsPipeline pipeline : pipelines) {
            try {
                List<Map<String, Object>> pipelineResults = pipeline.run(rawData);
                results.addAll(pipelineResults);
                log.info("[PluginRegistry] Pipeline '{}' produced {} result(s)", pipeline.getName(), pipelineResults.size());
            } catch (Exception e) {
                log.error("[PluginRegistry] Pipeline '{}' failed: {}", pipeline.getName(), e.getMessage());
            }
        }
        return results;
    }
}
