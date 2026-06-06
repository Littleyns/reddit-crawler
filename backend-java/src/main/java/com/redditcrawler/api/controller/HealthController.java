package com.redditcrawler.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    public HealthController(JdbcTemplate jdbcTemplate, RedisTemplate<String, String> redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> components = new LinkedHashMap<>();
        boolean allHealthy = true;

        Map<String, Object> dbStatus = checkDatabase();
        components.put("database", dbStatus);
        if (!"UP".equals(dbStatus.get("status"))) { allHealthy = false; }

        Map<String, Object> redisStatus = checkRedis();
        components.put("redis", redisStatus);
        if (!"UP".equals(redisStatus.get("status"))) { allHealthy = false; }

        response.put("healthy", allHealthy);
        response.put("components", components);
        response.put("timestamp", java.time.Instant.now().toString());

        return allHealthy ? ResponseEntity.ok(response) : ResponseEntity.status(503).body(response);
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> live() {
        return ResponseEntity.ok(Map.of("status", "alive"));
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> status = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT 1 AS alive");
            if (!rows.isEmpty()) { status.put("status", "UP"); return status; }
        } catch (Exception e) {}
        status.put("status", "INITIALIZING");
        status.put("error", "Database not yet available or under initialization");
        return status;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> status = new LinkedHashMap<>();
        try {
            String ping = redisTemplate.getConnectionFactory().getConnection().ping();
            if (ping != null) { status.put("status", "UP"); return status; }
        } catch (Exception e) {}
        status.put("status", "INITIALIZING");
        status.put("error", "Redis not yet connected");
        return status;
    }
}
