package com.redditcrawler.api.plugins.interfaces;

import java.util.List;

/**
 * Base interface for analytics pipeline plugins.
 */
public interface IAnalyticsPipeline {

    String getName();

    String getType();

    /** Run the pipeline on raw data and return results. */
    List<java.util.Map<String, Object>> run(List<ICrawlerPlugin.RawData> rawData);
}
