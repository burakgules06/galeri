package com.galeri.app.model.response;

import com.galeri.app.model.enums.Ownership;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Sermaye ve donem kari cevaplari (sadece ADMIN). Tutarlar kurus, paylar baz puan (10000 = %100).
public final class CapitalResponses {

    private CapitalResponses() {}

    /**
     * Ortak: sermayesi CAPITAL hesabinin bakiyesinden hesaplanir, hicbir yerde saklanmaz. Sermaye = nakitteki + araclarda
     * bagli (stoktaki ortak araclardaki odenmis katkilari). Pay toplam sermayeden; cekis en fazla nakitteki kadar.
     */
    public record Partner(UUID contactId, String name, String phone, boolean owner, long capitalCents,
                          long cashCents, long boundCents, int shareBp) {}

    /**
     * Donemde satilan arac. profitCents aracin kari (konsinyede komisyon); poolProfitCents havuza kalan kisim:
     * galeri aracinda kar, konsinyede komisyon, ortak aracta havuzun payi + dukkan payi (ortaklarin payi dusulmus).
     */
    public record SoldVehicle(VehicleRef vehicle, Ownership ownership, LocalDate saleDate, long salePriceCents,
                              Long costCents, long profitCents, long poolProfitCents) {}

    /**
     * Ortagin bu donemdeki payi: capitalCents agirligi = donem sonu nakitteki sermayesi (araclarda bagli kismi ve bu
     * donemin karindan eklenen haric); onerilen = net kar x pay; allocated = donem icinde sermayeye eklenmis olan.
     */
    public record PartnerProfit(UUID contactId, String name, boolean owner, long capitalCents, int shareBp,
                                long suggestedCents, long allocatedCents) {}

    /**
     * Havuzun donem kari = galeri araclarinin kari + ortak araclarda havuzun payi (dukkan payi dahil) + komisyonlar
     * - genel giderler (MIMARI §5). coOwnedProfitCents ortak araclarda ortaklara yazilan kar paylari: satista dogrudan
     * sermayelerine gecer, nete girmez (bilgi). Paylar donem sonundaki nakitteki sermayelerden.
     */
    public record PeriodProfit(LocalDate from, LocalDate to, List<SoldVehicle> vehicles,
                               long vehicleProfitCents, long commissionCents, long generalExpenseCents, long netCents,
                               long coOwnedProfitCents, List<PartnerProfit> partners) {}
}
