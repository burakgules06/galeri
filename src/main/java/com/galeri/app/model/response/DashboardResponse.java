package com.galeri.app.model.response;

import java.util.List;
import java.util.UUID;

// Ana ekran ozeti, sadece ADMIN. Tutarlar kurus; paylar baz puan (10000 = %100).
// receivable: bize borclu olanlar (cari bakiyesi arti), payable: bizim borclu oldugumuz (cari bakiyesi eksi).
public record DashboardResponse(
        long cashCents,
        long bankCents,
        long capitalCents,
        long receivableCents,
        long payableCents,
        List<PartnerShare> partners,
        List<ContactBalance> receivables,
        List<ContactBalance> payables,
        int inStockCount,
        long stockCostCents,
        long averageDaysInStock,
        List<MonthStat> months,
        int overdueCount,               // vadesi gecmis, odenmemis tahsilatlar (satis taksitleri)
        long overdueCents,
        List<InstallmentResponse> week, // bugun ve sonraki 6 gun vadesi gelen odenmemis taksitler (tahsilat ve odeme)
        long weekCollectCents,
        long weekPayCents
) {

    /** Sermaye ortagi: sermayesi ve toplam sermayedeki payi. owner = dukkan sahibi. */
    public record PartnerShare(UUID contactId, String name, boolean owner, long capitalCents, int shareBasisPoints) {}

    /** Bir kisinin carisi: amountCents her zaman pozitif, yonu listeden belli. */
    public record ContactBalance(UUID contactId, String name, String phone, long amountCents) {}

    /** Son aylar: alinan / satilan adet, satis cirosu ve satilan araclarin kari. */
    public record MonthStat(int year, int month, int bought, int sold, long revenueCents, long profitCents) {}
}
