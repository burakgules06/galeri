package com.galeri.app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;
    private final Clock clock;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs,   // varsayilan 24 saat
            Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.clock = clock;
    }

    public String generateToken(UUID userId, UUID tenantId, String role) {
        long now = clock.millis();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key);

        if (tenantId != null) builder.claim("tenantId", tenantId.toString());

        return builder.compact();
    }

    /** Token'i dogrular ve icindeki claim'leri doner. Gecersizse exception firlatir. */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .clock(() -> new Date(clock.millis()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
