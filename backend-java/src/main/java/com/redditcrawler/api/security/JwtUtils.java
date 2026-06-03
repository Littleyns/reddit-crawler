package com.redditcrawler.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT utility class for encoding and decoding tokens.
 */
public class JwtUtils {

    private final SecretKey key;
    private final long accessTokenMs;

    public JwtUtils(String base64Key, long minutes) {
        byte[] keyBytes = Decoders.BASE64.decode(base64Key);
        if (keyBytes.length < 32) {
            String padded = base64Key + "==";
            keyBytes = Decoders.BASE64.decode(padded);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenMs = minutes * 60L * 1000L;
    }

    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenMs);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String parseSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public long getAccessTokenMs() {
        return accessTokenMs;
    }
}
