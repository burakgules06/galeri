package com.galeri.app.util;

import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.CommissionType;

import java.util.ArrayList;
import java.util.List;

// Konsinye komisyonu ve ortakli aracta karin paylara bolunmesi. Hepsi kurus, tam sayi.
public final class ConsignmentMath {

    public static final int FULL_SHARE_BP = 10_000;

    private ConsignmentMath() {}

    /**
     * Konsinye aracin komisyonu karin uzerinden (Kasim 2026): kar = satis - sahibinin alis bedeli (maliyet bossa 0; masraflar
     * dahil degil). Zararda ya da karsiz satista 0. RATE: kar * baz puan / 10000, kurusa yarim yukari yuvarlanir
     * (800.000 maliyet, 1.000.000 satis, %3 -> 6.000 TL; 10,50 TL'nin %1,5'i = 0,1575 -> 0,16 TL). FIXED: sabit tutar, en fazla kar.
     * Satis bedeli yoksa: oranda null, sabitte tutarin kendisi.
     */
    public static Long commission(Vehicle v, Long salePriceCents) {
        if (v.getCommissionType() == null) return null;
        if (salePriceCents == null) return v.getCommissionType() == CommissionType.FIXED ? v.getCommissionCents() : null;
        long cost = v.getActualPurchaseCents() == null ? 0 : v.getActualPurchaseCents();
        long profit = Math.max(0, salePriceCents - cost);
        if (v.getCommissionType() == CommissionType.FIXED) {
            return v.getCommissionCents() == null ? null : Math.min(v.getCommissionCents(), profit);
        }
        if (v.getCommissionRateBp() == null) return null;
        return (profit * v.getCommissionRateBp() + FULL_SHARE_BP / 2) / FULL_SHARE_BP;
    }

    /**
     * Tutari paylara boler; her pay asagi yuvarlanir, kalan kurus(lar) ilk paya (en buyuk pay once gelir) eklenir.
     * Toplam her zaman tutara esittir. Zararda (eksi tutar) da ayni kural.
     */
    public static List<Long> split(long amountCents, List<Integer> sharesBp) {
        List<Long> parts = new ArrayList<>();
        long given = 0;
        for (int bp : sharesBp) {
            long part = Math.floorDiv(amountCents * bp, FULL_SHARE_BP);
            parts.add(part);
            given += part;
        }
        if (!parts.isEmpty()) parts.set(0, parts.get(0) + amountCents - given);
        return parts;
    }
}
