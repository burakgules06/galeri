package com.galeri.app.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Satis: alici zorunlu, bedel kurus. Taksitlerin toplami bedele kurusu kurusuna esit olmali (bkz. docs/MIMARI.md §6).
public record VehicleSaleRequest(
        @NotNull LocalDate saleDate,
        @NotNull UUID buyerId,
        UUID soldById,
        @NotNull @Positive Long salePriceCents,
        @NotEmpty List<@Valid InstallmentRequest> installments
) {}
