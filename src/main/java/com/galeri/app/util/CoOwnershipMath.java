package com.galeri.app.util;

import com.galeri.app.model.enums.CommissionType;

import java.util.ArrayList;
import java.util.List;

// Ortak aracta tutarin sahiplere bolunmesi ve dukkan payi. Kurus, tam sayi; parcalar toplami her zaman tutara esit.
public final class CoOwnershipMath {

    public static final int FULL_BP = 10_000;

    private CoOwnershipMath() {}

    /**
     * Tutari baz puanlara boler. Ortaklarin payi asagi yuvarlanir, kalan kurus Havuz'a (poolIndex) yazilir; Havuz yoksa
     * (poolIndex < 0) MoneyMath.splitByWeights gibi en buyuk paya. Eksi tutarda (zarar) da ayni kural.
     */
    public static List<Long> split(long amountCents, List<Integer> bps, int poolIndex) {
        if (poolIndex < 0) return MoneyMath.splitByWeights(amountCents, bps.stream().map(Integer::longValue).toList());
        List<Long> parts = new ArrayList<>();
        long given = 0;
        for (int k = 0; k < bps.size(); k++) {
            long part = k == poolIndex ? 0 : Math.floorDiv(amountCents * bps.get(k), FULL_BP);
            parts.add(part);
            given += part;
        }
        parts.set(poolIndex, amountCents - given);
        return parts;
    }

    /**
     * Dukkan payi: ortaklarin toplam kar payindan havuza kalan. Oranda kurusa yarim yukari yuvarlanir, sabitte en fazla
     * ortaklarin kari kadar. Kar yoksa (sifir ya da zarar) dukkan payi yok.
     */
    public static long shopCut(long partnersProfitCents, CommissionType type, Integer rateBp, Long fixedCents) {
        if (type == null || partnersProfitCents <= 0) return 0;
        if (type == CommissionType.FIXED) return Math.min(fixedCents == null ? 0 : fixedCents, partnersProfitCents);
        return (partnersProfitCents * (rateBp == null ? 0 : rateBp) + FULL_BP / 2) / FULL_BP;
    }
}
