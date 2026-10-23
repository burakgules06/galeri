package com.galeri.app.security;

import java.util.UUID;

// JWT'den cozulen oturum bilgisi. tenantId SUPER_ADMIN icin null.
public record PanelPrincipal(UUID userId, UUID tenantId, String role) {}
