package com.redditcrawler.api.plugins.kernel;

import com.redditcrawler.api.plugins.interfaces.ICrawlerPlugin;
import com.redditcrawler.api.plugins.interfaces.IAnalyticsPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Central registry for the Micro-Kernel plugin system.
 */
@Component
public class PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistry.class);

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

    @SuppressWarnings("unchecked")
    public Map<String, Object> runCrawls(String query) {
        log.info("[PluginRegistry] Running crawls for: {}", query);
        List<Object> results = new ArrayList<>();
        int totalProcessed = 0;
        for (ICrawlerPlugin connector : connectors) {
            try {
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
