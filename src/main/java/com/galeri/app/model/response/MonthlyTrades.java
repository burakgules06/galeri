package com.galeri.app.model.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Calisan bazinda aylik alim-satim gorunumu. Tutar YOK.
public record MonthlyTrades(int year, int month, List<StaffMonthly> staff) {

    /** staffId null ise "Belirtilmemis" grubu (araca calisan girilmemis). */
    public record StaffMonthly(UUID staffId, String name, List<MonthlyVehicle> bought, List<MonthlyVehicle> sold) {}

    public record MonthlyVehicle(UUID vehicleId, String plate, String make, String model, LocalDate date) {}
}
