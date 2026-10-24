package com.galeri.app.model.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Yil boyunca alinan / satilan araclar, calisan bazinda. Tutar YOK (bkz. docs/MIMARI.md §8).
// Panel aylara ve secilen calisana gore kendisi gruplar. staffId null = calisan girilmemis ("Belirtilmemis").
public record YearlyTrades(int year, List<StaffSummary> staff, List<TradeItem> items) {

    public enum Kind { BOUGHT, SOLD }

    public record TradeItem(UUID vehicleId, String plate, String make, String model, short modelYear, int mileageKm,
                            Kind kind, LocalDate date, UUID staffId, String staffName) {}
}
