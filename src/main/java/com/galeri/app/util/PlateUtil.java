package com.galeri.app.util;

import com.galeri.app.exception.runtime.InvalidPlateException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turk plakasini standart "34 ABC 123" bicimine getirir.
 * Bicim: il kodu (01-81) + 1-3 harf + 2-4 rakam. Bosluk, tire ve kucuk harf kabul edilir.
 */
public final class PlateUtil {

    private static final Pattern PLATE = Pattern.compile("^(\\d{2})([A-Z]{1,3})(\\d{2,4})$");

    private PlateUtil() {}

    public static String normalize(String raw) {
        if (raw == null) throw new InvalidPlateException();
        String clean = raw.replace('i', 'I').replace('ı', 'I').replace('İ', 'I')
                .toUpperCase(Locale.ROOT)
                .replaceAll("[\\s\\-.]", "");
        Matcher m = PLATE.matcher(clean);
        if (!m.matches()) throw new InvalidPlateException();
        int province = Integer.parseInt(m.group(1));
        if (province < 1 || province > 81) throw new InvalidPlateException();
        return m.group(1) + " " + m.group(2) + " " + m.group(3);
    }
}
