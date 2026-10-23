package com.galeri.app.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Servislerde oturumdaki kullaniciya/tenant'a erismek icin.
 * Tenant HER ZAMAN buradan alinir, istek govdesinden degil.
 */
public final class CurrentUser {

    private CurrentUser() {}

    public static PanelPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof PanelPrincipal p)) {
            throw new IllegalStateException("Oturum bulunamadi");
        }
        return p;
    }

    public static UUID tenantId() {
        UUID tenantId = principal().tenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Bu islem icin bir isletmeye bagli kullanici gerekir");
        }
        return tenantId;
    }
}
