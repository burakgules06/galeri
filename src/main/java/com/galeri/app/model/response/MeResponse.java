package com.galeri.app.model.response;

import java.util.UUID;

public record MeResponse(UUID userId, String fullName, String role, UUID tenantId, String tenantName) {}
