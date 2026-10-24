package com.galeri.app.model.response;

import com.galeri.app.model.enums.AccountType;

import java.util.UUID;

// Bakiye hareketlerden hesaplanir (giren - cikan), kurus.
public record AccountResponse(UUID id, AccountType type, String name, long balanceCents) {}
