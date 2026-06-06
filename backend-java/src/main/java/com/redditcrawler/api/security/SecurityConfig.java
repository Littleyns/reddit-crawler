package com.redditcrawler.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the Reddit Crawler backend.
 * JWT validation is handled by JwtAuthFilter (order=1) which runs before Spring's filter chain.
 * This class delegates unauthenticated requests that skip JWT to permit public endpoints,
 * and requires auth for API routes.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_GET_ENDPOINTS = {
        "/",
        "/actuator/health",
        "/actuator/info",
        "/error",
        "/api/h2-console/**"
    };

    private static final String[] PUBLIC_API_GET = {
        "/api/crawler/status/**",
        "/api/niche/**",
        "/api/analysis/**"
    };

    // Auth endpoints never require prior authentication — you must be able to log in without being logged in.
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
        "/api/auth/login",
        "/api/auth/register",
        "/api/auth/verify"
    };

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configure(http))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public GET endpoints (no auth needed)
                .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                // Public API read endpoints
                .requestMatchers(HttpMethod.GET, PUBLIC_API_GET).permitAll()
                // Auth endpoints (login/register/verify) — never require prior authentication
                .requestMatchers("/api/auth/**").permitAll()
                // All other POST/PUT/DELETE require authentication
                .requestMatchers(HttpMethod.POST, "/api/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/**").authenticated()
                // Everything else permit all (allow H2 console, Swagger, etc.)
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // Disable HTTP 403 default entry point — let filter handle it or route to error page
            .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, authFailed) -> {
                res.setStatus(401);
                res.setContentType("application/json");
                res.setCharacterEncoding("UTF-8");
                res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"JWT token required\"}");
            }))
        ;

        return http.build();
    }
}
