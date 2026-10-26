package com.galeri.app.model.response;

import com.galeri.app.model.enums.CommissionType;

import java.util.List;
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
        UUID purchaseAccountId,   // alis odemesinin ciktigi kasa/banka; deftere yazilmadiysa null
        ContactSummary consignor, // konsinye: aracin sahibi
        CommissionType commissionType,
        Integer commissionRateBp,
        Long commissionCents,
        List<ShareResponse> shares, // ortak arac: sahipler (Havuz dahil), paylari ve katkilari
        CommissionType shopCutType, // ortak arac: istege bagli dukkan payi
        Integer shopCutRateBp,
        Long shopCutCents
) {}
