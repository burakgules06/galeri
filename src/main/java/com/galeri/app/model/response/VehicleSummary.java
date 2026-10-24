package com.galeri.app.model.response;

import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.enums.VehicleStatus;

import java.time.LocalDate;
import java.util.UUID;

// Liste satiri. Para alani YOK; hem ADMIN hem STAFF'a ayni doner.
public record VehicleSummary(
        UUID id,
        String plate,
        String make,
        String model,
        short modelYear,
        int mileageKm,
        Fuel fuel,
        Transmission transmission,
        String color,
        VehicleStatus status,
        LocalDate purchaseDate,
        LocalDate saleDate,
        long daysInStock,
        DamageSummary damage
) {}
