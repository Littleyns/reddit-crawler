package com.redditcrawler.api.security;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter — intercepts requests, validates the Bearer token,
 * and sets the SecurityContextHolder if valid.
 */
@Component
@Order(1)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String AUTH_PREFIX = "Bearer ";

    private final UserService userService;
    private final String jwtSecret;

    public JwtAuthFilter(UserService userService, @Value("${app.jwt.secret}") String jwtSecret) {
        this.userService = userService;
        this.jwtSecret = jwtSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(AUTH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(AUTH_PREFIX.length());

        try {
            JwtUtils jwtUtils = new JwtUtils(jwtSecret, 60L);
            String username = jwtUtils.parseSubject(token);

            if (username == null) {
                filterChain.doFilter(request, response);
                return;
            }

            log.debug("Authenticated user: {}", username);
        } catch (Exception e) {
            log.error("JWT validation failed", e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
