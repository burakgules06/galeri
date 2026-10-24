package com.galeri.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private CorsConfiguration cors(List<String> izinliler) {
        SecurityConfig config = new SecurityConfig(mock(JwtAuthFilter.class), mock(RequestLoggingFilter.class), izinliler);
        return config.corsConfigurationSource().getCorsConfiguration(new MockHttpServletRequest("GET", "/api/araclar"));
    }

    @Test
    void devDeseni_herLocalhostPortunuKabulEder() {
        CorsConfiguration c = cors(List.of("http://localhost:*", "http://127.0.0.1:*"));

        assertThat(c.checkOrigin("http://localhost:5173")).isNotNull();
        assertThat(c.checkOrigin("http://localhost:5174")).isNotNull();
        assertThat(c.checkOrigin("http://127.0.0.1:5173")).isNotNull();
        assertThat(c.checkOrigin("https://kotu-site.com")).isNull();
    }

    @Test
    void prodAdresi_sadeceKendisiniKabulEder() {
        CorsConfiguration c = cors(List.of("https://galeri.akillirandevum.com"));

        assertThat(c.checkOrigin("https://galeri.akillirandevum.com")).isNotNull();
        assertThat(c.checkOrigin("https://galeri.akillirandevum.com.kotu.com")).isNull();
        assertThat(c.checkOrigin("http://localhost:5173")).isNull();
    }
}
