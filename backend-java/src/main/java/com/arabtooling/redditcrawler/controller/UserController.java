package com.arabtooling.redditcrawler.controller;

import com.arabtooling.redditcrawler.dto.UserProfileResponse;
import com.arabtooling.redditcrawler.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final AuthService authService;

    @GetMapping("/profile/{userId}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(authService.getUserProfile(userId));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteMyAccount() {
        Long userId = getCurrentUserId();
        authService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            // TODO: Get user ID from token claims
            return 1L;
        }
        throw new IllegalStateException("User not authenticated");
    }
}
