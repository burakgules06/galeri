package com.galeri.app.model.response;

import com.galeri.app.model.enums.PlanDirection;

import java.util.List;
import java.util.UUID;

// Aracin odeme plani ozeti ve taksitleri (kurus). Sadece ADMIN.
public record PaymentPlanResponse(
        UUID id,
        PlanDirection direction,
        UUID vehicleId,
        ContactSummary counterparty,
        long totalCents,
        long paidCents,
        long remainingCents,
        long overdueCents,
        List<InstallmentResponse> installments
) {}
