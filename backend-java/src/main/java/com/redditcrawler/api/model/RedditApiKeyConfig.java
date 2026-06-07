package com.redditcrawler.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a stored Reddit OAuth API key configuration entry,
 * enabling multi-config support and round-robin rotation.
 */
@Entity
@Table(name = "reddit_api_keys", indexes = {
    @Index(name = "idx_api_key_alias", columnList = "alias")
})
public class RedditApiKeyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String clientId;

    @Column(nullable = false, length = 256)
    private String clientSecret;

    @Column(length = 100)
    private String alias; // human-friendly name for this key config

    @Column(columnDefinition = "INTEGER DEFAULT 0")
    private int rotationOrder; // round-robin ordering

    @Column(columnDefinition = "BOOLEAN DEFAULT true")
    private boolean active;

    @Column(length = 256)
    private String refreshToken;

    @Column(length = 100)
    private String accessToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public RedditApiKeyConfig() {}

    public RedditApiKeyConfig(String clientId, String clientSecret, int order, boolean active, String alias) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.rotationOrder = order;
        this.active = active;
        this.alias = alias;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public int getRotationOrder() { return rotationOrder; }
    public void setRotationOrder(int order) { this.rotationOrder = order; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(LocalDateTime tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
}
