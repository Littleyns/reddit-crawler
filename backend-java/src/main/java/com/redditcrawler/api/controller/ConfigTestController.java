package com.redditcrawler.api.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates Reddit credentials and connectivity via the /api/config endpoint.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigTestController {

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testConfig() {
        Map<String, Object> response = new LinkedHashMap<>();

        String clientId = System.getenv("REDDIT_CLIENT_ID");
        String clientSecret = System.getenv("REDDIT_CLIENT_SECRET");
        String userAgent = System.getenv("REDDIT_USER_AGENT");

        if (clientId == null || clientId.isEmpty()) {
            response.put("status", "error");
            response.put("message", "REDDIT_CLIENT_ID environment variable is not set.");
            response.put("username", null);
            return ResponseEntity.status(400).body(response);
        }

        if (clientSecret == null || clientSecret.isEmpty()) {
            response.put("status", "error");
            response.put("message", "REDDIT_CLIENT_SECRET environment variable is not set.");
            response.put("username", null);
            return ResponseEntity.status(400).body(response);
        }

        if (userAgent == null || userAgent.isEmpty()) {
            userAgent = "reddit-crawler/1.0";
        }

        try {
            String basicAuth = clientId + ":" + clientSecret;
            String encoded = java.util.Base64.getEncoder().encodeToString(basicAuth.getBytes());

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encoded);
            headers.set("User-Agent", userAgent);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> result = restTemplate.exchange(
                    "https://www.reddit.com/",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (result.getStatusCode().is2xxSuccessful()) {
                response.put("status", "ok");
                response.put("message", "Credentials valid. Connected to Reddit successfully.");
                response.put("username", null);
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "error");
                response.put("message", "Invalid credentials - Reddit returned: " + result.getStatusCode());
                response.put("username", null);
                return ResponseEntity.status(result.getStatusCode().value()).body(response);
            }

        } catch (Exception ex) {
            // Also log the error in application logging framework in production
            response.put("status", "error");
            response.put("message", "Failed to contact Reddit API: " + ex.getMessage());
            response.put("username", null);
            return ResponseEntity.badRequest().body(response);
        }
    }
}
