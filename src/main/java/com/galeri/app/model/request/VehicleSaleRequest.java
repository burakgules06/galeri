package com.galeri.app.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Satis: alici (kisi) ya da buyerUserId (panel kullanicisi) zorunlu, bedel kurus.
// Kisiye satista taksitlerin toplami bedele kurusu kurusuna esit olmali (bkz. docs/MIMARI.md §6).
// Kullaniciya satista taksit yok: bedelin buyerCapitalCents kadari alanin sermayesinden, kalani cashAccountId'ye nakit.
// transfer = "satmak icin al": arac alanin stoguna yeni kayitla girer, maliyeti satis bedeli (MIMARI §0 madde 5).
public record VehicleSaleRequest(
        @NotNull LocalDate saleDate,
        UUID buyerId,
        UUID soldById,
        @NotNull @Positive Long salePriceCents,
        List<@Valid InstallmentRequest> installments,
        // Ortak aracta ortaga donus secimi (bos = herkesinki sermayede kalir)
        List<@Valid OwnerReturnRequest> ownerReturns,
        UUID buyerUserId,
        Boolean transfer,
        @PositiveOrZero Long buyerCapitalCents,
        UUID cashAccountId
) {
    public VehicleSaleRequest(LocalDate saleDate, UUID buyerId, UUID soldById, Long salePriceCents, List<InstallmentRequest> installments,
                              List<OwnerReturnRequest> ownerReturns) {
        this(saleDate, buyerId, soldById, salePriceCents, installments, ownerReturns, null, null, null, null);
    }

    public VehicleSaleRequest(LocalDate saleDate, UUID buyerId, UUID soldById, Long salePriceCents, List<InstallmentRequest> installments) {
        this(saleDate, buyerId, soldById, salePriceCents, installments, null);
    }

    public boolean transferOrDefault() {
        return Boolean.TRUE.equals(transfer);
    }
}
