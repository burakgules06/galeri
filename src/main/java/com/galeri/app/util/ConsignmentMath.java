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
     * Konsinye aracin komisyonu. RATE: satis * baz puan / 10000, kurusa yarim yukari yuvarlanir
     * (1.300.000,00 TL'nin %3'u = 39.000,00 TL; 10,50 TL'nin %1,5'i = 0,1575 -> 0,16 TL). FIXED: sabit tutar.
     * Oran tipinde satis bedeli yoksa null.
     */
    public static Long commission(Vehicle v, Long salePriceCents) {
        if (v.getCommissionType() == null) return null;
        if (v.getCommissionType() == CommissionType.FIXED) return v.getCommissionCents();
        if (salePriceCents == null || v.getCommissionRateBp() == null) return null;
        return (salePriceCents * v.getCommissionRateBp() + FULL_SHARE_BP / 2) / FULL_SHARE_BP;
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
