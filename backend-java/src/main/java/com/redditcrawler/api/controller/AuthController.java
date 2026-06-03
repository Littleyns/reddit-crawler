package com.redditcrawler.api.controller;

import com.redditcrawler.api.security.JwtUtils;
import com.redditcrawler.api.security.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for authentication endpoints.
 * All endpoints are public (no auth required) — handled by SecurityConfig permitAll.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserService userService, @Value("${app.jwt.secret}") String jwtSecret) {
        this.userService = userService;
        // 30-minute access token by default for API routes.
        this.jwtUtils = new JwtUtils(jwtSecret, 30);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String username = payload.get("email");
        String password = payload.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Both 'email' and 'password' are required"
            ));
        }

        var optPass = userService.findByUsername(username);
        if (optPass.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Invalid credentials"
            ));
        }

        if (!passwordEncoder.matches(password, optPass.get())) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Invalid credentials"
            ));
        }

        // Generate JWT access token
        String token = jwtUtils.generateToken(username);

        log.info("User '{}' logged in successfully", username);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of(
                        "id", username,  // Using username as the user identifier for now
                        "email", username,
                        "role", "USER"   // TODO: load role from DB when users table is wired up
                )
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        String username = payload.get("email");
        String password = payload.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Both 'email' and 'password' are required"
            ));
        }

        if (userService.findByUsername(username).isPresent()) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Conflict",
                    "message", "User already exists"
            ));
        }

        userService.register(username, password);

        // Auto-login after registration
        String token = jwtUtils.generateToken(username);

        log.info("User '{}' registered and logged in successfully", username);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of(
                        "id", username,
                        "email", username,
                        "role", "USER"
                )
        ));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify() {
        // Returns 200 with a placeholder since full token verification is handled by JwtAuthFilter
        // Frontend can check auth status by hitting this endpoint after login.
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "message", "Authentication service available"
        ));
    }
}
