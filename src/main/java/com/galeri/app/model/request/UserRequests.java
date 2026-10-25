package com.galeri.app.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Kullanici yonetimi istekleri. Sifre en az 8 karakter (BCrypt 72 bayttan sonrasini okumaz).
public final class UserRequests {

    private UserRequests() {}

    public record Create(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(max = 20) String phone,
            @NotNull @Pattern(regexp = "ADMIN|STAFF") String role,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {}

    public record Update(
            @NotBlank @Size(max = 120) String fullName,
            @NotNull @Pattern(regexp = "ADMIN|STAFF") String role,
            @NotNull Boolean active
    ) {}

    public record ResetPassword(@NotBlank @Size(min = 8, max = 72) String password) {}

    public record ChangePassword(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {}
}
