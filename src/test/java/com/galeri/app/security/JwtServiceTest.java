package com.galeri.app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-en-az-otuz-iki-karakter-uzunlugunda";
    private static final Instant NOW = Instant.parse("2026-07-01T08:00:00Z");

    private JwtService serviceAt(Instant instant, long expirationMs) {
        return new JwtService(SECRET, expirationMs, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void uretilenToken_ayniBilgilerleCozulur() {
        JwtService jwt = serviceAt(NOW, 60_000);
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Claims claims = jwt.parseToken(jwt.generateToken(userId, tenantId, "ADMIN"));

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("tenantId", String.class)).isEqualTo(tenantId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void superAdminTokeni_tenantIcermez() {
        JwtService jwt = serviceAt(NOW, 60_000);

        Claims claims = jwt.parseToken(jwt.generateToken(UUID.randomUUID(), null, "SUPER_ADMIN"));

        assertThat(claims.get("tenantId", String.class)).isNull();
    }

    @Test
    void suresiDolanToken_reddedilir() {
        String token = serviceAt(NOW, 60_000).generateToken(UUID.randomUUID(), UUID.randomUUID(), "STAFF");
        JwtService ikiDakikaSonra = serviceAt(NOW.plusSeconds(120), 60_000);

        assertThatThrownBy(() -> ikiDakikaSonra.parseToken(token)).isInstanceOf(ExpiredJwtException.class);
    }
}
