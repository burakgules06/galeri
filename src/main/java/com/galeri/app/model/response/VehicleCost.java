package com.galeri.app.model.response;

import com.galeri.app.model.enums.Ownership;

import java.util.List;
import java.util.UUID;

// Arac maliyeti ve kari (kurus). Kar sadece satilmis aracta hesaplanir.
// - DEALER / CO_OWNED: maliyet = gercek alis + araca bagli masraflar, kar = satis - maliyet.
// - CONSIGNMENT: alis yok; expenseCents = galerinin ustlendigi masraflar, ownerChargedCents = sahibine yansitilanlar,
//   kar = komisyon - galerinin ustlendigi masraflar. ownerPayoutCents = sahibine borclanilan (satis - komisyon).
// purchaseInLedger: alis odemesi deftere yazildi mi. shares: ortakli aracta karin ortaklara bolunmesi (sadece gosterim).
public record VehicleCost(
        UUID vehicleId,
        Ownership ownership,
        long purchaseCents,
        boolean purchaseInLedger,
        long expenseCents,
        long totalCents,
        Long salePriceCents,
        Long profitCents,
        Long commissionCents,
        Long ownerPayoutCents,
        long ownerChargedCents,
        List<ShareResponse> shares,
        List<LedgerEntryResponse> entries
) {}
