package com.galeri.app.model.request;

import com.galeri.app.model.enums.OwnerReturnMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

// Ortak arac satisinda bir ortagin alacagi (katki + kar payi) ne olacak. CASH: tamami, HYBRID: amountCents kadari
// accountId (kasa / banka) hesabindan nakit odenir (sermaye cekisi). Listede olmayan ortak icin KEEP (sermayede kalir).
public record OwnerReturnRequest(
        @NotNull UUID userId,
        @NotNull OwnerReturnMode mode,
        @Positive Long amountCents,
        UUID accountId
) {}
