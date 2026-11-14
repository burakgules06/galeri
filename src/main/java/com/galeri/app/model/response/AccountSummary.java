package com.galeri.app.model.response;

import com.galeri.app.model.enums.AccountType;

import java.util.UUID;

// userId: sermaye / cari hesabinin sahibi (kasa / bankada null). Iki sermaye hesabi arasindaki hareketin yonu icin (aktarim, borc).
public record AccountSummary(UUID id, AccountType type, String name, UUID userId) {}
