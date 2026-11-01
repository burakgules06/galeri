package com.galeri.app.model.response;

import com.galeri.app.model.enums.ContributionSource;

import java.util.UUID;

// Ortak aracin bir sahibi. contributionCents: o ana kadar odenmis katkisi (planli alista odendikce artar). Satilmis aracta
// receivableCents satistan alacagi (satis x kar payi orani - masraf payi - dukkan payi), profitCents = alacak - katki;
// collectedCents tahsil edildikce ona dagitilan. Stoktaysa uculu de null.
public record ShareResponse(
        UUID userId,
        String name,
        boolean owner,   // dukkan sahibinin payi: parasi kasadan, kari donem karina (eski Havuz)
        int shareBp,
        int profitShareBp,
        ContributionSource source,
        Long pocketCents,
        long contributionCents,
        Long profitCents,
        Long receivableCents,
        Long collectedCents   // satilmis aracta tahsil edilip bu sahibe dagitilan; alacak - bu = tahsil edilmemis payi
) {}
