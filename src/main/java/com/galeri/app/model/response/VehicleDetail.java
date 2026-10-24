package com.galeri.app.model.response;

import com.galeri.app.model.enums.BodyPart;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.PartCondition;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.enums.VehicleStatus;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

// Teknik bilgiler + hasar semasi. Para alani YOK (tramer hasar kaydi teknik bilgi sayilir).
// STAFF bunu alir; ADMIN bunu VehicleDetailAdmin icinde alir.
public record VehicleDetail(
        UUID id,
        String plate,
        String make,
        String model,
        short modelYear,
        int mileageKm,
        Fuel fuel,
        Transmission transmission,
        String color,
        Long tramerCents,
        String notes,
        VehicleStatus status,
        LocalDate purchaseDate,
        LocalDate saleDate,
        long daysInStock,
        Map<BodyPart, PartCondition> parts
) {}
