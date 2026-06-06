package com.redditcrawler.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables @Async (async crawler executions) and @Scheduled (periodic health/status checks).
 * Creates a custom thread pool for sentiment processing separate from the general async executor.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * General-purpose async executor for crawl-related async work (started crawlers, batch jobs).
     */
    @Bean(name = "crawlExecutor")
    public Executor crawlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);            // 2 concurrent crawls by default
        executor.setMaxPoolSize(5);             // burst up to 5
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("crawl-");
        executor.initialize();
        log.info("[AsyncConfig] crawlExecutor: core=2 max=5 queue=100");
        return executor;
    }

    /**
     * Dedicated thread pool for sentiment/LLM batch processing — heavier CPU bound.
     */
    @Bean(name = "sentimentExecutor")
    public Executor sentimentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);            // 3 parallel LLM workers
        executor.setMaxPoolSize(8);             // burst for large batches
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sentiment-");
        executor.initialize();
        log.info("[AsyncConfig] sentimentExecutor: core=3 max=8 queue=50");
        return executor;
    }

    /**
     * Scheduler task executor for @Scheduled methods.
     * Spring's task-scheduler handles the thread pool automatically — no separate bean needed.
     */
    @Bean(name = "schedulerTaskExecutor")
    public TaskExecutor schedulerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setThreadNamePrefix("async-sched-");
        executor.initialize();
        return executor;
    }
}
