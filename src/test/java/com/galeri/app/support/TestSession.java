package com.galeri.app.support;

import com.galeri.app.security.PanelPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

// Birim testlerinde CurrentUser'in okudugu oturumu kurar.
public final class TestSession {

    private TestSession() {}

    public static void open(UUID userId, UUID tenantId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                new PanelPrincipal(userId, tenantId, role), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void close() {
        SecurityContextHolder.clearContext();
    }
}
