package com.redditcrawler.api.plugins.interfaces;

import java.util.List;

/**
 * Base interface for all Reddit Crawler plugins.
 * Connectors implement this to expose crawl capabilities.
 */
public interface ICrawlerPlugin {

    /** Plugin metadata for registration. */
    String getName();

    /** Plugin type: CONNECTOR | ANALYTICS | NLP | VISUALIZATION */
    String getType();

    /** Raw data entry from a crawl run. */
    class RawData {
        public final String id;
        public final String title;
        public final String content;
        public final String source;

        public RawData(String id, String title, String content, String source) {
            this.id = id != null ? id : "";
            this.title = title != null ? title : "";
            this.content = content != null ? content : "";
            this.source = source != null ? source : "unknown";
        }

        @Override
        public String toString() {
            return "RawData{id='" + id + "', title='" + title + "'}";
        }
    }
}
