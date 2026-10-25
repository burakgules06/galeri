package com.galeri.app.model.response;

import com.galeri.app.model.enums.Ownership;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Sermaye ve donem kari cevaplari (sadece ADMIN). Tutarlar kurus, paylar baz puan (10000 = %100).
public final class CapitalResponses {

    private CapitalResponses() {}

    /** Ortak: sermayesi CAPITAL hesabinin bakiyesinden hesaplanir, hicbir yerde saklanmaz. */
    public record Partner(UUID contactId, String name, String phone, boolean owner, long capitalCents, int shareBp) {}

    /** Donemde satilan arac. CONSIGNMENT'ta profitCents komisyondur; CO_OWNED havuz disi (net kara girmez). */
    public record SoldVehicle(VehicleRef vehicle, Ownership ownership, LocalDate saleDate, long salePriceCents,
                              Long costCents, long profitCents) {}

    /** Ortagin bu donemdeki payi: onerilen = net kar x pay; allocated = donem icinde sermayeye eklenmis olan. */
    public record PartnerProfit(UUID contactId, String name, boolean owner, long capitalCents, int shareBp,
                                long suggestedCents, long allocatedCents) {}

    /**
     * Donem kari = satilan galeri araclarinin kari + komisyonlar - genel giderler (MIMARI §5).
     * coOwnedProfitCents ayri gosterilir, nete girmez (ozel ortakli arac havuz disi).
     * Paylar donem sonundaki sermayelerden.
     */
    public record PeriodProfit(LocalDate from, LocalDate to, List<SoldVehicle> vehicles,
                               long vehicleProfitCents, long commissionCents, long generalExpenseCents, long netCents,
                               long coOwnedProfitCents, List<PartnerProfit> partners) {}
}
