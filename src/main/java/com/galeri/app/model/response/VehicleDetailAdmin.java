package com.galeri.app.model.response;

import java.util.UUID;

// Sadece ADMIN'e doner: teknik detay + kisiler + para alanlari (kurus).
public record VehicleDetailAdmin(
        VehicleDetail vehicle,
        ContactSummary seller,
        StaffSummary boughtBy,
        Long actualPurchaseCents,
        Long officialPurchaseCents,
        ContactSummary buyer,
        StaffSummary soldBy,
        Long salePriceCents,
        UUID purchaseAccountId    // alis odemesinin ciktigi kasa/banka; deftere yazilmadiysa null
) {}
