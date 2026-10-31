package com.galeri.app.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Kullanici yonetimi istekleri. Sifre en az 8 karakter (BCrypt 72 bayttan sonrasini okumaz).
public final class UserRequests {

    private UserRequests() {}

    /**
     * role: ADMIN (dukkan sahibi), INVESTOR (sermayedar), STAFF (calisan). canLogin false ise telefon ve sifre bos olabilir
     * (or. telefonu olmayan sermayedar). canTrade sadece calisanda anlamli (arac alim / satimi girebilir).
     */
    public record Create(
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 20) String phone,
            @NotNull @Pattern(regexp = "ADMIN|INVESTOR|STAFF") String role,
            @Size(min = 8, max = 72) String password,
            Boolean canLogin,
            Boolean canTrade
    ) {
        public Create(String fullName, String phone, String role, String password) {
            this(fullName, phone, role, password, true, false);
        }
    }

    public record Update(
            @NotBlank @Size(max = 120) String fullName,
            @NotNull @Pattern(regexp = "ADMIN|INVESTOR|STAFF") String role,
            @NotNull Boolean active,
            Boolean canLogin,   // bos: degismez
            Boolean canTrade,   // bos: degismez
            @Size(max = 20) String phone,   // bos: degismez
            @Size(min = 8, max = 72) String password   // giris acilirken sifresi yoksa; bos: degismez
    ) {
        public Update(String fullName, String role, Boolean active) {
            this(fullName, role, active, null, null, null, null);
        }
    }

    public record ResetPassword(@NotBlank @Size(min = 8, max = 72) String password) {}

    public record ChangePassword(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {}
}
