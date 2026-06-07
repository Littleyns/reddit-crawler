package com.redditcrawler.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@Configuration
@EntityScan(basePackages = "com.redditcrawler.api.model")
@EnableJpaRepositories(basePackages = "com.redditcrawler.api.repository")
public class RedditApiRotationConfig {
    // Marker config to scan RedditApiKey entity into the JPA context
}
