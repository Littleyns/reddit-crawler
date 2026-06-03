package com.redditcrawler.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for system settings endpoints.
 */
@RestController
@RequestMapping("/api")
public class SettingsController {

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("apiKey", "");
        settings.put("defaultSubreddit", "machinelearning");
        settings.put("defaultDepth", 4);
        settings.put("defaultLimit", 250);
        settings.put("autoExport", false);
        settings.put("exportFormat", "csv");
        settings.put("sessionTimeoutMinutes", 45);
        settings.put("users", Map.of("id", "admin", "email", "admin@redditcrawler.com", "role", "ADMIN"));
        return ResponseEntity.ok(settings);
    }

    @PostMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("apiKey", payload.getOrDefault("apiKey", ""));
        response.put("defaultSubreddit", payload.getOrDefault("defaultSubreddit", "machinelearning"));
        response.put("defaultDepth", payload.getOrDefault("defaultDepth", 4));
        response.put("defaultLimit", payload.getOrDefault("defaultLimit", 250));
        response.put("autoExport", payload.getOrDefault("autoExport", false));
        response.put("exportFormat", payload.getOrDefault("exportFormat", "csv"));
        response.put("sessionTimeoutMinutes", payload.getOrDefault("sessionTimeoutMinutes", 45));
        return ResponseEntity.ok(response);
    }
}
