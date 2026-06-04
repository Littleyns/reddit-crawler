package com.redditcrawler.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Spring @EnableAsync configuration for parallel sentiment analysis tasks.
 * Used by SentimentAnalysisController.batchAnalyze for high-throughput processing.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Dedicated thread pool for NLP/sentiment analysis tasks.
     * Core 4, max 8 threads — sized to match server cores without starving other beans.
     */
    @Bean(name = "sentimentExecutor")
    public Executor sentimentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sentiment-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        log.info("Initialized sentimentExecutor: core=4 max=8 queueCapacity=100");
        return executor;
    }
}
