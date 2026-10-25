package com.galeri.app.model.response;

import java.util.UUID;

// Ortakli aracta bir ortak ve payi (baz puan). profitCents: satilmis aracta kardaki payi (sadece gosterim), yoksa null.
public record ShareResponse(UUID contactId, String name, int shareBp, Long profitCents) {}
