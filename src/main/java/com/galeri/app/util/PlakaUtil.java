package com.galeri.app.util;

import com.galeri.app.exception.runtime.GecersizPlakaException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turk plakasini standart "34 ABC 123" bicimine getirir.
 * Bicim: il kodu (01-81) + 1-3 harf + 2-4 rakam. Bosluk, tire ve kucuk harf kabul edilir.
 */
public final class PlakaUtil {

    private static final Pattern PLAKA = Pattern.compile("^(\\d{2})([A-Z]{1,3})(\\d{2,4})$");

    private PlakaUtil() {}

    public static String normalize(String raw) {
        if (raw == null) throw new GecersizPlakaException();
        String temiz = raw.replace('i', 'I').replace('ı', 'I').replace('İ', 'I')
                .toUpperCase(Locale.ROOT)
                .replaceAll("[\\s\\-.]", "");
        Matcher m = PLAKA.matcher(temiz);
        if (!m.matches()) throw new GecersizPlakaException();
        int il = Integer.parseInt(m.group(1));
        if (il < 1 || il > 81) throw new GecersizPlakaException();
        return m.group(1) + " " + m.group(2) + " " + m.group(3);
    }
}
