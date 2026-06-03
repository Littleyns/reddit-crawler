package com.arabtooling.redditcrawler.service;

import com.arabtooling.redditcrawler.dto.LoginRequest;
import com.arabtooling.redditcrawler.dto.RegisterRequest;
import com.arabtooling.redditcrawler.dto.RefreshTokenRequest;
import com.arabtooling.redditcrawler.dto.UserProfileResponse;
import com.arabtooling.redditcrawler.entity.RefreshToken;
import com.arabtooling.redditcrawler.entity.User;
import com.arabtooling.redditcrawler.exception.AuthException;
import com.arabtooling.redditcrawler.repository.RefreshTokenRepository;
import com.arabtooling.redditcrawler.repository.UserRepository;
import com.arabtooling.redditcrawler.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.access-token-expiration-ms:86400000}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    @Transactional
    public Authentication authenticate(Authentication authentication) {
        return authenticationManager.authenticate(authentication);
    }

    @Override
    public org.springframework.security.core.userdetails.User loadUserByUsername(String username) 
            throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Validate password match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Check if username already exists
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new AuthException("Username already exists");
        }

        // Check if email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AuthException("Email already exists");
        }

        // Create new user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("ROLE_USER");

        User savedUser = userRepository.save(user);

        return createAuthResponse(savedUser, true);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Load user details
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AuthException("Invalid username or password"));

        return createAuthResponse(user, false);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // Validate refresh token
        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new AuthException("Invalid refresh token"));

        // Check if token is expired
        if (token.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new AuthException("Refresh token has expired");
        }

        // Get user
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new AuthException("User not found"));

        // Generate new token pair
        return createAuthResponse(user, false);
    }

    private AuthResponse createAuthResponse(User user, boolean isRegistration) {
        String accessToken = jwtUtil.generateToken(user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        // Save refresh token to database
        RefreshToken savedToken = new RefreshToken();
        savedToken.setUserId(user.getId());
        savedToken.setToken(refreshToken);
        savedToken.setExpiresAt(Instant.now().plus(refreshTokenExpirationMs, ChronoUnit.MILLIS));
        refreshTokenRepository.save(savedToken);

        AuthResponse response = new AuthResponse();
        response.setToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setExpiresIn(accessTokenExpirationMs);

        return response;
    }

    @Transactional
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setCreatedAt(user.getCreatedAt());
        response.setSessionCount(user.getSessions() != null ? user.getSessions().size() : 0);

        // Calculate total posts scraped across all sessions
        int totalPosts = 0;
        if (user.getSessions() != null) {
            for (var session : user.getSessions()) {
                totalPosts += session.getPostsScraped();
            }
        }
        response.setTotalPostsScraped(totalPosts);

        return response;
    }

    @Transactional
    public void logout(Long userId) {
        // Invalidate all refresh tokens for this user
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }
}
