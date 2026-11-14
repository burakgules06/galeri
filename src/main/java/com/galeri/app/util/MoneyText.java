package com.galeri.app.util;

import java.text.NumberFormat;
import java.util.Locale;

// Kurusu kullaniciya giden metin icin TL'ye cevirir: 150000000 -> "1.500.000 TL", 12345 -> "123,45 TL".
public final class MoneyText {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    private MoneyText() {}

    public static String tl(long cents) {
        NumberFormat f = NumberFormat.getNumberInstance(TURKISH);
        f.setMinimumFractionDigits(cents % 100 == 0 ? 0 : 2);
        f.setMaximumFractionDigits(2);
        return f.format(cents / 100.0) + " TL";
    }
}
