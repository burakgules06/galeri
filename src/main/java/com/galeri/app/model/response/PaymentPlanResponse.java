package com.galeri.app.model.response;

import com.galeri.app.model.enums.PlanDirection;

import java.util.List;
import java.util.UUID;

// Aracin odeme plani ozeti ve taksitleri (kurus). Sadece ADMIN.
// basket: toplu satista sepetteki araclar ve bedelleri (normal planda bos); vehicleId toplu satista ve gecmis kayitta bos.
public record PaymentPlanResponse(
        UUID id,
        PlanDirection direction,
        UUID vehicleId,
        ContactSummary counterparty,
        long totalCents,
        long paidCents,
        long remainingCents,
        long overdueCents,
        List<InstallmentResponse> installments,
        List<BasketItem> basket
) {
    public record BasketItem(VehicleRef vehicle, long salePriceCents) {}

    public PaymentPlanResponse(UUID id, PlanDirection direction, UUID vehicleId, ContactSummary counterparty, long totalCents,
                               long paidCents, long remainingCents, long overdueCents, List<InstallmentResponse> installments) {
        this(id, direction, vehicleId, counterparty, totalCents, paidCents, remainingCents, overdueCents, installments, List.of());
    }
}
