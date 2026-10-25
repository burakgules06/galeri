package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

// vehicleId doluysa arac masrafi (aracin maliyetine eklenir), bossa genel gider.
// Kategori: categoryId ya da categoryName; ad ile gelirse yoksa o an olusturulur.
public record ExpenseRequest(
        @NotNull LocalDate date,
        @NotNull UUID sourceAccountId,
        @NotNull @Positive Long amountCents,
        UUID vehicleId,
        UUID categoryId,
        @Size(max = 60) String categoryName,
        UUID parentCategoryId,
        @Size(max = 500) String description,
        // Sadece konsinye aracta: true ise masraf sahibinin carisine yazilir (sahibi galeriye borclanir),
        // degilse galerinin genel gideridir. Diger araclarda yok sayilir.
        Boolean chargeToOwner
) {}
