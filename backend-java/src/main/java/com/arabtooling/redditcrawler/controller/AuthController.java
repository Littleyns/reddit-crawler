package com.arabtooling.redditcrawler.controller;

import com.arabtooling.redditcrawler.dto.LoginRequest;
import com.arabtooling.redditcrawler.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication endpoints")
@RequiredArgsConstructor
public class AuthController {

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Logs in a user and returns JWT tokens")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // TODO: Implement JWT token generation with Spring Security
        // This is a placeholder - need to integrate with actual auth service
        
        LoginResponse response = new LoginResponse();
        response.setUsername(request.getUsername());
        response.setRole("ADMIN");
        response.setToken("placeholder-token-" + request.getUsername());
        response.setRefreshToken("placeholder-refresh-token");
        
        return ResponseEntity.ok(response);
    }
}
