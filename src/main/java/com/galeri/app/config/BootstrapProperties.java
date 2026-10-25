package com.galeri.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.stream.Stream;

// Ilk kurulum: BOOTSTRAP_* degiskenleri. Prod'da bir kez .env'e yazilir, servis acilinca silinir.
@ConfigurationProperties(prefix = "bootstrap")
public record BootstrapProperties(String tenantName, String tenantSlug, String adminName, String adminPhone, String adminPassword) {

    private Stream<String> values() {
        return Stream.of(tenantName, tenantSlug, adminName, adminPhone, adminPassword);
    }

    public boolean anySet() {
        return values().anyMatch(v -> v != null && !v.isBlank());
    }

    public boolean allSet() {
        return values().allMatch(v -> v != null && !v.isBlank());
    }
}
