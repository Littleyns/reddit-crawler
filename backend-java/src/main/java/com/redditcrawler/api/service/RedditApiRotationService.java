package com.redditcrawler.api.service;

import com.redditcrawler.api.model.RedditApiKeyConfig;
import com.redditcrawler.api.repository.RedditApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Base64;

/**
 * Multi-config Reddit API key rotation service.
 * Manages a pool of Reddit OAuth credentials and rotates them on a
 * round-robin basis, handling automatic token refresh when needed.
 */
@Service
public class RedditApiRotationService {

    private static final Logger log = LoggerFactory.getLogger(RedditApiRotationService.class);

    private final RedditApiKeyRepository apiKeyRepo;
    private final RestTemplate restTemplate;
    private final AtomicInteger rotationIndex = new AtomicInteger(0);

    @Autowired
    public RedditApiRotationService(RedditApiKeyRepository apiKeyRepo, RestTemplate restTemplate) {
        this.apiKeyRepo = apiKeyRepo;
        this.restTemplate = restTemplate;
    }

    /**
     * Get the current active Reddit API key config (round-robin).
     */
    public RedditApiKeyConfig getCurrentApiKey() {
        List<RedditApiKeyConfig> activeKeys = getValidActiveKeys();
        if (activeKeys.isEmpty()) {
            log.warn("No active Reddit API keys configured. Falling back to system default.");
            return null;
        }

        // Round-robin: increment index and pick the next key
        int idx = rotationIndex.getAndUpdate(i -> (i + 1) % activeKeys.size());
        RedditApiKeyConfig key = activeKeys.get(idx);

        // Check if current access token is expired; refresh if needed
        if (needsTokenRefresh(key)) {
            log.info("Token for alias '{}' expired — auto-refreshing", key.getAlias());
            refreshToken(key);
        }

        return key;
    }

    /**
     * Get all valid, active API keys sorted by rotation order.
     */
    public List<RedditApiKeyConfig> getAllActiveKeys() {
        return apiKeyRepo.findByActiveTrueOrderByRotationOrderAsc();
    }

    private List<RedditApiKeyConfig> getValidActiveKeys() {
        LocalDateTime now = LocalDateTime.now();
        List<RedditApiKeyConfig> activeKeys = apiKeyRepo.findByActiveTrueOrderByRotationOrderAsc();

        // Filter out keys whose tokens are expired and can't be refreshed
        return activeKeys.stream()
                .filter(key -> key.getTokenExpiresAt() == null || !key.getTokenExpiresAt().isBefore(now))
                .toList();
    }

    /**
     * Check if a token needs refreshing (expires within next 5 minutes).
     */
    private boolean needsTokenRefresh(RedditApiKeyConfig key) {
        if (key.getAccessToken() == null || key.getAccessToken().isEmpty()) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        // Consider a token expired if it's past expiry or will expire within 5 minutes
        return !now.isBefore(key.getTokenExpiresAt().minusMinutes(5));
    }

    /**
     * Rotate the active key to a specific index (for manual testing).
     */
    public void setRotationIndex(int idx) {
        List<RedditApiKeyConfig> keys = getValidActiveKeys();
        if (!keys.isEmpty()) {
            rotationIndex.set(idx % keys.size());
            log.info("Manually set rotation index to {}", idx);
        }
    }

    /**
     * Add a new Reddit API key configuration.
     */
    @Transactional
    public RedditApiKeyConfig addApiKey(String clientId, String clientSecret, String alias) {
        int maxOrder = apiKeyRepo.findAllByOrderByRotationOrderAsc().stream()
                .mapToInt(RedditApiKeyConfig::getRotationOrder)
                .max()
                .orElse(-1);

        RedditApiKeyConfig config = new RedditApiKeyConfig(clientId, clientSecret, maxOrder + 1, true, alias);
        log.info("Added new Reddit API key config with alias '{}', rotation order {}", alias, config.getRotationOrder());
        return apiKeyRepo.save(config);
    }

    /**
     * Remove/delete an API key configuration.
     */
    @Transactional
    public void removeApiKey(Long id) {
        apiKeyRepo.deleteById(id);
        log.info("Removed Reddit API key config with ID {}", id);
    }

    /**
     * Get all configured keys (for management UI).
     */
    public List<RedditApiKeyConfig> getAllKeys() {
        return apiKeyRepo.findAllByOrderByRotationOrderAsc();
    }

    /**
     * Authenticate with Reddit using this key config and store access token.
     */
    @Transactional
    public boolean refreshToken(RedditApiKeyConfig config) {
        try {
            String credentials = config.getClientId() + ":" + config.getClientSecret();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "Basic " + encoded);

            String tokenUrl = "https://www.reddit.com/api/v1/access_token";
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(tokenUrl)
                    .queryParam("grant_type", "client_credentials");

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    uriBuilder.build().toUri(), HttpMethod.POST, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> tokenData = parseJsonResponse(response.getBody());
                String accessToken = (String) tokenData.get("access_token");
                Integer expiresIn = (Integer) tokenData.get("expires_in");
                String refreshTokenStr = (String) tokenData.get("refresh_token");

                LocalDateTime expiresAt;
                if (expiresIn != null && expiresIn > 0) {
                    expiresAt = LocalDateTime.now().plusSeconds(expiresIn);
                } else {
                    expiresAt = LocalDateTime.now().plusHours(1); // default
                }

                apiKeyRepo.updateTokens(config.getId(), accessToken, refreshTokenStr, expiresAt);

                log.info("Successfully refreshed token for alias '{}', expires at {}", config.getAlias(), expiresAt);
                return true;
            } else {
                log.error("Failed to refresh token for alias '': got status {}", config.getAlias(), response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error refreshing Reddit API token for alias '{}': {}", config.getAlias(), e.getMessage());
            return false;
        }
    }

    /**
     * Get a fresh access token string for the current rotation index.
     */
    public String getCurrentAccessToken() {
        RedditApiKeyConfig key = getCurrentApiKey();
        if (key == null || key.getAccessToken() == null) {
            // Try to refresh all keys first, then pick one
            refreshTokenAllKeys();
            key = getCurrentApiKey();
        }
        return key != null ? key.getAccessToken() : null;
    }

    /**
     * Attempt to refresh tokens for all non-expired keys.
     */
    @Transactional
    public void refreshTokenAllKeys() {
        List<RedditApiKeyConfig> keys = apiKeyRepo.findByActiveTrueOrderByRotationOrderAsc();
        log.info("Attempting token refresh for {} API configs", keys.size());

        LocalDateTime now = LocalDateTime.now().plusMinutes(5);
        List<Long> expiredIds = apiKeyRepo.findInvalidTokens(now).stream()
                .map(RedditApiKeyConfig::getId)
                .toList();

        int refreshed = 0;
        for (RedditApiKeyConfig key : keys) {
            if (expiredIds.contains(key.getId()) || needsTokenRefresh(key)) {
                boolean result = refreshToken(key);
                if (!result) {
                    // Mark this config as inactive so it's not used for new requests
                    apiKeyRepo.setActive(key.getId(), false);
                    log.warn("Disabled API key config '{}' after refresh failure", key.getAlias());
                } else {
                    refreshed++;
                }
            }
        }

        log.info("Token refresh complete: {} succeeded, {} skipped/disabled", refreshed, keys.size() - refreshed);
    }

    /**
     * Simple JSON parser for the token response.
     */
    private Map<String, Object> parseJsonResponse(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        String trimmed = json.trim().replaceAll("\\s+", "");
        // Very basic extractor - just looks for "key":value patterns
        String[] pairs = trimmed.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].replaceAll("\"", "").trim();
                String value = kv[1].replaceAll("[{}]", "").trim().replaceAll("\"", "");
                // Try to parse as int or long
                try {
                    result.put(key, Long.parseLong(value));
                } catch (NumberFormatException ex) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }
}
