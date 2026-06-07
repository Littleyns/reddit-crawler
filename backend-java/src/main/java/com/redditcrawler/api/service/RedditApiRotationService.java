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
     * Get the active API key WITHOUT advancing the round-robin index.
     * Used by crawler to pick a key once per crawl session.
     */
    public RedditApiKeyConfig peekCurrentApiKey() {
        List<RedditApiKeyConfig> activeKeys = getValidActiveKeys();
        if (activeKeys.isEmpty()) {
            return null;
        }
        int idx = Math.floorMod(rotationIndex.get(), activeKeys.size());
        RedditApiKeyConfig key = activeKeys.get(idx);

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
     * Check if the access token is expired or will expire within 5 minutes.
     */
    public boolean needsTokenRefresh(RedditApiKeyConfig key) {
        if (key.getTokenExpiresAt() == null) return false;
        LocalDateTime window = LocalDateTime.now().plusMinutes(5);
        return key.getTokenExpiresAt().isBefore(window);
    }

    /**
     * Get the access token for a given key config.
     * Returns null if the key has no refresh token (needs user OAuth flow).
     */
    public String getAccessTokenFor(RedditApiKeyConfig key, RestTemplate rt) {
        if (key == null) return null;

        // If we already have an access token and it's not expiring within 5 min, reuse it
        if (!needsTokenRefresh(key)) {
            log.debug("Reusing existing access token for alias '{}'", key.getAlias());
            return key.getAccessToken();
        }

        // Need to get a new token — use refresh flow or client_credentials
        refreshToken(key);
        return key.getAccessToken();
    }

    /**
     * Add a new API key config.
     */
    @Transactional
    public RedditApiKeyConfig addApiKey(String clientId, String clientSecret, String alias) {
        log.info("[P4-1] Adding new Reddit API key config with alias '{}'", alias);

        RedditApiKeyConfig config = new RedditApiKeyConfig();
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        config.setAlias(alias != null && !alias.isEmpty() ? alias : ("key-" + System.currentTimeMillis()));
        config.setActive(true);

        // Determine rotation order
        int maxOrder = apiKeyRepo.findAllByOrderByRotationOrderAsc().stream()
                .mapToInt(RedditApiKeyConfig::getRotationOrder)
                .max()
                .orElse(-1);
        config.setRotationOrder(maxOrder + 1);

        return apiKeyRepo.save(config);
    }

    /**
     * Remove an API key config by ID.
     */
    @Transactional
    public RedditApiKeyConfig removeApiKey(Long id) {
        log.info("[P4-1] Removing Reddit API key config: id={}", id);
        Optional<RedditApiKeyConfig> existing = apiKeyRepo.findById(id);

        if (existing.isPresent()) {
            // Deactivate rather than physically delete to maintain referential integrity
            existing.get().setActive(false);
            return apiKeyRepo.save(existing.get());
        }

        log.warn("[P4-1] API key config not found for deletion: id={}", id);
        throw new NoSuchElementException("API key config with id " + id + " not found");
    }

    /**
     * Refresh tokens for all active keys that need it.
     */
    @Transactional
    public void refreshTokenAllKeys() {
        log.info("[P4-1] Refreshing OAuth tokens for all valid active Reddit API key configs...");

        List<RedditApiKeyConfig> keys = apiKeyRepo.findByActiveTrueOrderByRotationOrderAsc();
        LocalDateTime now = LocalDateTime.now();

        int refreshed = 0;
        int failed = 0;

        for (RedditApiKeyConfig config : keys) {
            // Try refresh if we have a refresh token
            if (config.getRefreshToken() != null && !config.getRefreshToken().isEmpty()) {
                if (refreshTokenByAlias(config) != null) {
                    refreshed++;
                } else {
                    failed++;
                }
            }
        }

        log.info("[P4-1] Token refresh summary — active keys: {}, refreshed: {}, failed: {}", 
                keys.size(), refreshed, failed);
    }

    /**
     * Return the number of valid active API key configurations.
     */
    @Transactional(readOnly = true)
    public int getActiveKeyCount() {
        List<RedditApiKeyConfig> activeKeys = getValidActiveKeys();
        return Math.max(activeKeys.size(), 0);
    }

    /**
     * Get a summary of all active keys for monitoring.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> rotationSummary() {
        List<RedditApiKeyConfig> keys = getAllActiveKeys();
        int count = getActiveKeyCount();
        LocalDateTime now = LocalDateTime.now();

        return Map.of(
            "activeKeys", keys.size(),
            "validTokens", count,
            "keys", keys.stream().map(k -> {
                // Check expiry without modifying state
                boolean expiringSoon = (k.getTokenExpiresAt() != null) && 
                                       k.getTokenExpiresAt().isBefore(now.plusMinutes(5));
                return Map.<String, Object>of(
                    "alias", k.getAlias(),
                    "rotationOrder", k.getRotationOrder(),
                    "active", k.isActive(),
                    "accessTokenPresent", k.getAccessToken() != null && !k.getAccessToken().isEmpty(),
                    "refreshTokenPresent", k.getRefreshToken() != null && !k.getRefreshToken().isEmpty(),
                    "expiresAt", k.getTokenExpiresAt(),
                    "expiringSoon", expiringSoon
                );
            }).toList()
        );
    }

    /** Refresh all keys including invalid ones */
    @Transactional(readOnly = true)
    public void refreshAllTokensIncludingInvalid(RedditApiKeyRepository repo, RestTemplate rt) {
        log.info("[P4-1] Refreshing tokens for ALL configured Reddit API key configs (including invalid ones)...");

        List<RedditApiKeyConfig> allKeys = apiKeyRepo.findAllByOrderByRotationOrderAsc();
        int successCount = 0;
        int failCount = 0;

        for (RedditApiKeyConfig config : allKeys) {
            if (refreshToken(config)) {
                successCount++;
            } else {
                failCount++;
                try {
                    log.warn("[P4-1] Token refresh failed for alias '{}'. Deactivating.", config.getAlias());
                    repo.setActive(config.getId(), false);
                } catch (Exception e) {
                    log.warn("[P4-1] Could not deactivate key: {}", e.getMessage());
                }
            }
        }

        log.info("[P4-1] Batch refresh done — OK: {}, FAIL: {}", successCount, failCount);
    }

    /** Refresh a single key's tokens via Reddit OAuth — exposed for scheduler use (P4-1) */
    public RedditApiKeyConfig refreshTokenByAlias(RedditApiKeyConfig config) {
        try {
            if (config.getRefreshToken() == null || config.getRefreshToken().isEmpty()) {
                refreshTokenViaClientCredentials(config);
                return apiKeyRepo.findByAlias(config.getAlias()).orElse(null);
            }

            String credBase64 = Base64.getEncoder().encodeToString(
                    (config.getClientId() + ":" + config.getClientSecret()).getBytes(StandardCharsets.ISO_8859_1));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + credBase64);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            Map<String, String> body = new LinkedHashMap<>();
            body.put("grant_type", "refresh_token");
            body.put("refresh_token", config.getRefreshToken());

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    "https://www.reddit.com/api/v1/access_token", HttpMethod.POST, entity, Map.class);

            if (resp.getBody() != null) {
                String accessToken = (String) resp.getBody().get("access_token");
                int expiresIn = ((Number) resp.getBody().getOrDefault("expires_in", 3600)).intValue();
                String refreshTokenStr = (String) resp.getBody().getOrDefault("refresh_token", config.getRefreshToken());
                LocalDateTime expiresAt = LocalDateTime.now()
                        .plusSeconds(expiresIn > 0 ? expiresIn : 3600);

                apiKeyRepo.updateTokens(config.getId(), accessToken, refreshTokenStr, expiresAt);
                log.info("[P4-1] Token refreshed for alias '{}' (expires in {}s)", config.getAlias(), expiresIn);
                return apiKeyRepo.findByAlias(config.getAlias()).orElse(null);
            } else {
                log.error("[P4-1] Token refresh returned empty body for alias '{}'", config.getAlias());
                // Fallback to client_credentials
                refreshTokenViaClientCredentials(config);
                return apiKeyRepo.findByAlias(config.getAlias()).orElse(null);
            }
        } catch (Exception e) {
            log.error("[P4-1] Token refresh failed for alias '" + config.getAlias() + "': " + e.getMessage(), e);
            refreshTokenViaClientCredentials(config);
            return apiKeyRepo.findByAlias(config.getAlias()).orElse(null);
        }
    }

    /** Refresh a single key's tokens via OAuth2 client_credentials or refresh_token grant */
    private boolean refreshToken(RedditApiKeyConfig config) {
        try {
            String credBase64 = Base64.getEncoder()
                    .encodeToString((config.getClientId() + ":" + config.getClientSecret())
                            .getBytes(StandardCharsets.ISO_8859_1));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + credBase64);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            Map<String, String> body = new LinkedHashMap<>();
            if (config.getRefreshToken() != null && !config.getRefreshToken().isEmpty()) {
                // Try refresh token flow first
                body.put("grant_type", "refresh_token");
                body.put("refresh_token", config.getRefreshToken());
            } else {
                body.put("grant_type", "client_credentials");
            }

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    "https://www.reddit.com/api/v1/access_token", HttpMethod.POST, entity, Map.class);

            if (resp.getBody() != null) {
                String accessToken = (String) resp.getBody().get("access_token");
                int expiresIn = ((Number) resp.getBody().getOrDefault("expires_in", 3600)).intValue();
                String refreshTokenStr = (String) resp.getBody().getOrDefault("refresh_token", config.getRefreshToken());
                LocalDateTime expiresAt = LocalDateTime.now()
                        .plusSeconds(expiresIn > 0 ? expiresIn : 3600);

                apiKeyRepo.updateTokens(config.getId(), accessToken, refreshTokenStr, expiresAt);
                log.info("[P4-1] Token refreshed for alias '{}' (expires in {}s)", config.getAlias(), expiresIn);
                return true;
            } else {
                log.error("[P4-1] Token refresh returned empty body for alias '{}'", config.getAlias());
                return false;
            }
        } catch (Exception e) {
            log.error("[P4-1] Token refresh failed for alias '" + config.getAlias() + "': " + e.getMessage(), e);
            // Try client_credentials as fallback if refresh token failed
            try {
                String credBase64 = Base64.getEncoder()
                        .encodeToString((config.getClientId() + ":" + config.getClientSecret())
                                .getBytes(StandardCharsets.ISO_8859_1));

                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Basic " + credBase64);
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                Map<String, String> body = new LinkedHashMap<>();
                body.put("grant_type", "client_credentials");

                HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<Map> resp = restTemplate.exchange(
                        "https://www.reddit.com/api/v1/access_token", HttpMethod.POST, entity, Map.class);

                if (resp.getBody() != null) {
                    String accessToken = (String) resp.getBody().get("access_token");
                    int expiresIn = ((Number) resp.getBody().getOrDefault("expires_in", 3600)).intValue();
                    LocalDateTime expiresAt = LocalDateTime.now()
                            .plusSeconds(expiresIn > 0 ? expiresIn : 3600);

                    apiKeyRepo.updateTokens(config.getId(), accessToken, null, expiresAt);
                    log.info("[P4-1] Token refreshed via client_credentials for alias '{}'", config.getAlias());
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e2) {
                log.error("[P4-1] Both refresh and client_credentials failed for alias '" + config.getAlias() + "': " + e2.getMessage(), e2);
                return false;
            }
        }
    }

    /** Refresh tokens using the Reddit API via OAuth2. */
    private ResponseEntity<Map> refreshTokenByAlias(RedditApiKeyConfig config) {
        try {
            if (config.getRefreshToken() == null || config.getRefreshToken().isEmpty()) {
                return refreshTokenViaClientCredentials(config);
            }

            String credBase64 = Base64.getEncoder().encodeToString(
                    (config.getClientId() + ":" + config.getClientSecret()).getBytes(StandardCharsets.ISO_8859_1));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + credBase64);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            Map<String, String> body = new LinkedHashMap<>();
            body.put("grant_type", "refresh_token");
            body.put("refresh_token", config.getRefreshToken());

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            return restTemplate.exchange(
                    "https://www.reddit.com/api/v1/access_token", HttpMethod.POST, entity, Map.class);
        } catch (Exception e) {
            log.warn("[P4-1] Token refresh via alias '{}' failed — trying client_credentials: {}", 
                    config.getAlias(), e.getMessage());
            return refreshTokenViaClientCredentials(config);
        }
    }

    private ResponseEntity<Map> refreshTokenViaClientCredentials(RedditApiKeyConfig config) {
        try {
            String credBase64 = Base64.getEncoder().encodeToString(
                    (config.getClientId() + ":" + config.getClientSecret()).getBytes(StandardCharsets.ISO_8859_1));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + credBase64);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            Map<String, String> body = new LinkedHashMap<>();
            body.put("grant_type", "client_credentials");

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            return restTemplate.exchange(
                    "https://www.reddit.com/api/v1/access_token", HttpMethod.POST, entity, Map.class);
        } catch (Exception e) {
            log.error("[P4-1] client_credentials refresh failed for alias '" + config.getAlias() + "': " + e.getMessage(), e);
            throw new RuntimeException("client_credentials refresh failed: " + e.getMessage());
        }
    }

    /** Get keys sorted by rotation order. */
    public List<RedditApiKeyConfig> getAllKeys() {
        return apiKeyRepo.findAllByOrderByRotationOrderAsc();
    }
}
