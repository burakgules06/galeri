package com.galeri.app.model.request;

import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Transmission;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

// Tutarlar kurus cinsinden (1.250.000,50 TL = 125000050).
// purchaseAccountId bos ise alis odemesi deftere yazilmaz (ornegin senetli alis, 3. adim).
public record VehicleRequest(
        @NotBlank String plate,
        @NotBlank @Size(max = 40) String make,
        @NotBlank @Size(max = 60) String model,
        @NotNull @Min(1950) @Max(2100) Short modelYear,
        @NotNull Fuel fuel,
        @NotNull Transmission transmission,
        @NotNull @PositiveOrZero Integer mileageKm,
        @Size(max = 30) String color,
        @PositiveOrZero Long tramerCents,
        String notes,
        @NotNull LocalDate purchaseDate,
        UUID sellerId,
        UUID boughtById,
        @PositiveOrZero Long actualPurchaseCents,
        @PositiveOrZero Long officialPurchaseCents,
        UUID purchaseAccountId    // pesin alis: gercek alis bedeli bu kasa/banka hesabindan deftere yazilir
) {}
