package com.arabtooling.redditcrawler.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "System", description = "System endpoints")
public class SystemController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns the health status of the API")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/version")
    @Operation(summary = "Get version", description = "Returns the API version")
    public ResponseEntity<String> version() {
        return ResponseEntity.ok("1.0.0");
    }
}
