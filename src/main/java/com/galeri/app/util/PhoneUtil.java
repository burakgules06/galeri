package com.galeri.app.util;

public final class PhoneUtil {

    private PhoneUtil() {}

    /** Loglama icin: son 4 hane haric hepsini * yapar. 905301112233 -> ********2233 */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    /** Normalize edilmis Turkiye cep telefonu mu: 905XXXXXXXXX. */
    public static boolean isValidMobile(String normalized) {
        return normalized != null && normalized.matches("^905\\d{9}$");
    }

    /**
     * Telefonu standart 90XXXXXXXXXX formatina cevirir.
     * 5301390726 / 05301390726 / +90 530 139 07 26 -> 905301390726
     * Cozumlenemezse temizlenmis rakamlari, bossa null doner.
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return null;
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.length() == 10 && digits.startsWith("5")) return "90" + digits;
        if (digits.length() == 11 && digits.startsWith("0")) return "90" + digits.substring(1);
        return digits;
    }
}
