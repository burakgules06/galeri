package com.galeri.app.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Toplu satis: ayni aliciya birden fazla arac, her birinin bedeli ve toplam uzerinden tek odeme plani.
// Taksitlerin toplami bedellerin toplamina esit olmali. Ortak aracta ortaga donus secilmez (paylar sermayede kalir).
public record BasketSaleRequest(
        @NotNull LocalDate saleDate,
        @NotNull UUID buyerId,
        UUID soldById,
        @NotEmpty @Size(min = 2, max = 20) List<@Valid Item> items,
        @NotEmpty List<@Valid InstallmentRequest> installments
) {
    public record Item(@NotNull UUID vehicleId, @NotNull @Positive Long salePriceCents) {}
}
