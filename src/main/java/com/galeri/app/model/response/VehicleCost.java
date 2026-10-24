package com.galeri.app.model.response;

import java.util.List;
import java.util.UUID;

// Arac maliyeti = gercek alis + araca bagli masraflar (kurus). Kar sadece satilmis aracta hesaplanir.
// purchaseInLedger: alis odemesi kasa/bankadan deftere yazildi mi.
public record VehicleCost(
        UUID vehicleId,
        long purchaseCents,
        boolean purchaseInLedger,
        long expenseCents,
        long totalCents,
        Long salePriceCents,
        Long profitCents,
        List<LedgerEntryResponse> entries
) {}
