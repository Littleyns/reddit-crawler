package com.redditcrawler.api.controller;

import com.redditcrawler.api.model.RedditApiKeyConfig;
import com.redditcrawler.api.service.RedditApiRotationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing multi-config Reddit API key rotation.
 * Exposes CRUD + management operations on the stored OAuth2/Client-Credentials configs.
 */
@RestController
@RequestMapping("/api/keys")
public class ApiKeysManagementController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeysManagementController.class);
    private final RedditApiRotationService rotationService;

    public ApiKeysManagementController(RedditApiRotationService rotationService) {
        this.rotationService = rotationService;
    }

    // ── List all configs ────────────────────────────────────────
    @GetMapping
    public ResponseEntity<?> getAllKeys() {
        List<RedditApiKeyConfig> keys = rotationService.getAllKeys();
        return ResponseEntity.ok(keys);
    }

    // ── Get active keys only ───────────────────────────────────
    @GetMapping("/active")
    public ResponseEntity<?> getActiveKeys() {
        List<RedditApiKeyConfig> keys = rotationService.getAllActiveKeys();
        return ResponseEntity.ok(keys);
    }

    // ── Add a new API key config ───────────────────────────────
    @PostMapping
    public ResponseEntity<?> addKey(@RequestBody Map<String, Object> body) {
        String clientId     = (String) body.get("clientId");
        String clientSecret = (String) body.get("clientSecret");
        String alias        = (String) body.getOrDefault("alias", "");

        if (clientId == null || clientSecret == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientId and clientSecret are required"));
        }
        RedditApiKeyConfig config = rotationService.addApiKey(clientId, clientSecret, alias);
        return ResponseEntity.status(201).body(config);
    }

    // ── Remove an API key config ───────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<?> removeKey(@PathVariable Long id) {
        rotationService.removeApiKey(id);
        return ResponseEntity.ok(Map.of("message", "removed"));
    }

    // ── Refresh all tokens ─────────────────────────────────────
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAll() {
        rotationService.refreshTokenAllKeys();
        return ResponseEntity.ok(Map.of("message", "tokens_refreshed"));
    }
}
