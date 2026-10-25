package com.galeri.app.security;

import com.galeri.app.repository.PanelUserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Token gecerli olsa da: pasif kullanici iceri giremez, rol degisikligi hemen uygulanir.
class JwtAuthFilterTest {

    private final JwtService jwt = new JwtService("test-secret-en-az-otuz-iki-karakter-uzunlugunda", 60_000,
            Clock.fixed(Instant.now(), ZoneOffset.UTC));
    private final PanelUserRepository users = mock(PanelUserRepository.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwt, users);
    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(String tokenRole) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/vehicles");
        req.addHeader("Authorization", "Bearer " + jwt.generateToken(userId, tenantId, tokenRole));
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void pasifKullanici_gecerliTokenlaDaGirisYapamaz() throws Exception {
        when(users.findActiveRole(userId)).thenReturn(Optional.empty());

        assertThat(run("ADMIN")).isNull();
    }

    @Test
    void rolDusurulenYonetici_hemenCalisanYetkisiAlir() throws Exception {
        when(users.findActiveRole(userId)).thenReturn(Optional.of("STAFF"));

        Authentication auth = run("ADMIN");

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_STAFF");
        assertThat(((PanelPrincipal) auth.getPrincipal()).tenantId()).isEqualTo(tenantId);
    }
}
