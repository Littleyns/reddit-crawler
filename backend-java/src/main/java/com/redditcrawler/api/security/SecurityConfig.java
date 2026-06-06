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
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Endpoints that don't require authentication — health probes, actuator, public info
    private static final String[] PUBLIC_GET_ENDPOINTS = {
        "/actuator/health",
        "/actuator/info",
        "/error",
        "/api/h2-console/**",
        "/api/health/**",
        "/api/config/test"
    };

    // Public API read endpoints — no auth required for data retrieval
    private static final String[] PUBLIC_API_GET = {
        "/api/crawler/status/**",
        "/api/anomaly/**",          // AnomalyDetectionController
        "/api/anomalies/**",       // also public anomalies endpoints
        "/api/analytics/**",       // AnalyticsRestController read endpoints
        "/api/analysis/**",        // IdeaExtraction / NlpPipeline etc. — most are GET/probe
        "/api/data/post",          // DataController POST endpoint is query-based
        "/api/niche/**"
    };

    // Auth endpoints — login/register/verify never require prior authentication
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
                .requestMatchers(HttpMethod.POST, "/api/data/post").permitAll()
                // Auth endpoints — never require prior authentication
                .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                // All other POST/PUT/DELETE require authentication
                .requestMatchers(HttpMethod.POST, "/api/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/**").authenticated()
                // Allow OPTIONS (preflight)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Everything else permit all (Swagger, REST docs, etc.)
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
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
