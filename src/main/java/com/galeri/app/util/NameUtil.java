package com.galeri.app.util;

import java.util.Locale;

/**
 * Kisi adi, renk gibi serbest metinleri tek bicime getirir. Tamami buyuk ("ALİ YILMAZ") ya da tamami kucuk ("ali yılmaz")
 * yazilmissa her kelimenin ilk harfi buyuk yapilir ("Ali Yılmaz"); Turkce i/İ ve ı/I dogru cevrilir. Karisik yazilmis metne
 * dokunulmaz (kullanici bilerek yazmistir: "BMW Yetkili Servisi", "McLaren"). Bosluklar tek bosluga iner.
 */
public final class NameUtil {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    private NameUtil() {}

    public static String normalize(String raw) {
        if (raw == null) return null;
        String clean = raw.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) return clean;
        boolean hasUpper = false, hasLower = false;
        for (int k = 0; k < clean.length(); k++) {
            char c = clean.charAt(k);
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
        }
        if (hasUpper && hasLower) return clean;
        return titleCase(clean.toLowerCase(TURKISH));
    }

    /** Bosluk, tire, nokta ve kesme isaretinden sonraki ilk harf buyur: "ayşe-nur", "o'neil", "ltd.şti." */
    private static String titleCase(String lower) {
        StringBuilder sb = new StringBuilder(lower.length());
        boolean start = true;
        for (int k = 0; k < lower.length(); k++) {
            char c = lower.charAt(k);
            if (start && Character.isLetter(c)) {
                sb.append(String.valueOf(c).toUpperCase(TURKISH));
                start = false;
            } else {
                sb.append(c);
                if (c == ' ' || c == '-' || c == '.' || c == '\'' || c == '(' || c == '/') start = true;
                else if (Character.isLetterOrDigit(c)) start = false;
            }
        }
        return sb.toString();
    }
}
