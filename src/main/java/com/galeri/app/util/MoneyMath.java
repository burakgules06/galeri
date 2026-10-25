package com.galeri.app.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

// Tutari agirliklara (ornegin ortak sermayeleri) bolme. Kurus, tam sayi; toplam her zaman tutara esit.
public final class MoneyMath {

    private MoneyMath() {}

    /**
     * Her pay asagi yuvarlanir, kalan kurus(lar) en buyuk agirliga eklenir. Agirliklar toplami 0 ise hepsi 0.
     * Buyuk tutar x buyuk sermaye tasmasin diye BigInteger.
     */
    public static List<Long> splitByWeights(long amountCents, List<Long> weights) {
        long total = weights.stream().mapToLong(Long::longValue).sum();
        List<Long> parts = new ArrayList<>();
        if (total <= 0) {
            weights.forEach(w -> parts.add(0L));
            return parts;
        }
        long given = 0;
        int largest = 0;
        for (int k = 0; k < weights.size(); k++) {
            long part = BigInteger.valueOf(amountCents).multiply(BigInteger.valueOf(weights.get(k)))
                    .divide(BigInteger.valueOf(total)).longValue();
            if (amountCents < 0 && BigInteger.valueOf(amountCents).multiply(BigInteger.valueOf(weights.get(k)))
                    .mod(BigInteger.valueOf(total)).signum() != 0) {
                part--;   // BigInteger.divide sifira dogru keser; eksi tutarda da asagi yuvarla
            }
            parts.add(part);
            given += part;
            if (weights.get(k) > weights.get(largest)) largest = k;
        }
        parts.set(largest, parts.get(largest) + amountCents - given);
        return parts;
    }

    /** Agirligin toplamdaki payi, baz puan (yuvarlanmis; sadece gosterim). */
    public static int basisPoints(long weight, long total) {
        return total <= 0 ? 0 : (int) Math.round(weight * 10000.0 / total);
    }
}
