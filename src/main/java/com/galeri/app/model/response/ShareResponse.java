package com.galeri.app.model.response;

import com.galeri.app.model.enums.ContributionSource;

import java.util.UUID;

// Ortak aracin bir sahibi. contactId null = Havuz. contributionCents: o ana kadar odenmis katkisi (planli alista
// odendikce artar). Satilmis aracta profitCents kar payi (dukkan payi dusulmus), receivableCents alacagi = katki + kar payi;
// stoktaysa ikisi de null.
public record ShareResponse(
        UUID contactId,
        String name,
        int shareBp,
        int profitShareBp,
        ContributionSource source,
        Long pocketCents,
        long contributionCents,
        Long profitCents,
        Long receivableCents
) {}
