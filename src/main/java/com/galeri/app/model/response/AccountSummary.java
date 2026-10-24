package com.galeri.app.model.response;

import com.galeri.app.model.enums.AccountType;

import java.util.UUID;

public record AccountSummary(UUID id, AccountType type, String name) {}
