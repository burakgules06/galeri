package com.galeri.app.model.response;

import java.util.UUID;

// canTrade: calisan pesin alim / satim girebilir mi (panel her acilista buradan tazeler)
public record MeResponse(UUID userId, String fullName, String role, UUID tenantId, String tenantName, boolean canTrade,
                         boolean canAddExpense) {}
