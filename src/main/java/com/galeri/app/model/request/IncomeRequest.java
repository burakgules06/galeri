package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

// Diger kazanc: secilen kasa / bankaya para girisi. vehicleId doluysa o aracin karina eklenir (satis donemine), bossa
// donem karinda "diger kazanc". Kategori masraftaki gibi: categoryId ya da categoryName (yoksa olusturulur).
public record IncomeRequest(
        @NotNull LocalDate date,
        @NotNull UUID targetAccountId,
        @NotNull @Positive Long amountCents,
        UUID vehicleId,
        UUID categoryId,
        @Size(max = 60) String categoryName,
        UUID parentCategoryId,
        @Size(max = 500) String description
) {}
