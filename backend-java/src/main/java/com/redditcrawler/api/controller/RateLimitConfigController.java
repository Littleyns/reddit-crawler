package com.redditcrawler.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

import com.redditcrawler.api.service.RedditRateLimiter;

/**
 * Runtime management endpoints for P5-1 rate-limit configuration.
 * Allows operators to inspect and adjust crawl pacing without restarts.
 */
@RestController
@RequestMapping("/api/ratelimit")
public class RateLimitConfigController {

    private final RedditRateLimiter rateLimiter;

    public RateLimitConfigController(RedditRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /** GET /api/ratelimit/status → dump current effective config */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(rateLimiter.getConfigDump());
    }

    /** POST /api/ratelimit/config → accept adjusted values (minDelay only) */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(
            @RequestBody(required = false) Map<String, Object> body) {

        if (body == null) {
            return ResponseEntity.ok(rateLimiter.getConfigDump());
        }

        double minDelayS = asDouble(body.get("minDelaySeconds"), rateLimiter.getMinDelay().getSeconds());
        if (minDelayS < 0.5 || minDelayS > 30) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "error", "minDelaySeconds must be between 0.5 and 30"
            ));
        }

        rateLimiter.setMinDelay(Duration.ofSeconds((int) Math.round(minDelayS)));

        java.util.LinkedHashMap<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("message", "Rate-limit config updated");
        response.putAll(rateLimiter.getConfigDump());
        return ResponseEntity.ok(response);
    }

    private static double asDouble(Object o, double fallback) {
        if (o == null) return fallback;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return fallback; }
    }
}
