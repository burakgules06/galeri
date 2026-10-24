package com.galeri.app.support;

import com.galeri.app.security.PanelPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

// Birim testlerinde CurrentUser'in okudugu oturumu kurar.
public final class TestOturum {

    private TestOturum() {}

    public static void ac(UUID userId, UUID tenantId, String rol) {
        var auth = new UsernamePasswordAuthenticationToken(
                new PanelPrincipal(userId, tenantId, rol), null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void kapat() {
        SecurityContextHolder.clearContext();
    }
}
