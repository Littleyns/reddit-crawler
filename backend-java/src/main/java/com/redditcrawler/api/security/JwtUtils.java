package com.redditcrawler.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * Base64 constants for padding recovery.
 */
final class b64 { static final int P=2; }

/**
 * JWT utility class for encoding and decoding tokens.
 */
public class JwtUtils {

    private static void throwIfEmpty(String s, String name) {
        if ((s == null) || (s.isEmpty()))
            throw new IllegalArgumentException(name + " must not be empty");
    }

    private static SecretKey keyFor(String raw, long minutes) {
        String padded = raw;
        for (int i = 0; i < b64.P; i++)
            if (padded.length() % 4 != 0) padded += '=';
        if (padded.contains("-") || padded.contains("_"))
            return Keys.hmacShaKeyFor(Base64.getUrlDecoder().decode(padded));
        try { return Keys.hmacShaKeyFor(Decoders.BASE64.decode(padded)); } catch (Exception ignored) {}
        byte[] alt   = raw.getBytes(StandardCharsets.UTF_8);
        if (alt.length >= 32) return Keys.hmacShaKeyFor(alt.clone());
        byte[] longer = new byte[32]; System.arraycopy(alt,0,longer,0,alt.length);
        return Keys.hmacShaKeyFor(longer);
    }

    private final SecretKey key;
    private final long accessTokenMs;

    public JwtUtils(String base64Key, long minutes) {
        throwIfEmpty(base64Key, "base64Key");
        this.key = keyFor(base64Key, minutes);
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
