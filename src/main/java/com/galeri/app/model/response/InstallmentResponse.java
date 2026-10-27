package com.galeri.app.model.response;

import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.PlanDirection;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// Taksit satiri: plan ve arac bilgisiyle. Sadece ADMIN'e doner. seq / count: "2 / 5" gosterimi icin.
// endorsedTo: ciro edildiyse cekin / senedin verildigi kisi (status PAID). bounced: karsiliksiz cikti ve hala acik.
public record InstallmentResponse(
        UUID id,
        UUID planId,
        PlanDirection direction,
        VehicleRef vehicle,
        int seq,
        int count,
        LocalDate dueDate,
        long amountCents,
        PaymentMethod method,
        String noteNo,
        ContactSummary payer,
        ContactSummary counterparty,
        InstallmentStatus status,
        long daysLate,
        UUID paidEntryId,
        OffsetDateTime paidAt,
        String bankName,
        String drawerName,
        ContactSummary endorsedTo,
        boolean bounced
) {
    public InstallmentResponse(UUID id, UUID planId, PlanDirection direction, VehicleRef vehicle, int seq, int count, LocalDate dueDate,
                               long amountCents, PaymentMethod method, String noteNo, ContactSummary payer, ContactSummary counterparty,
                               InstallmentStatus status, long daysLate, UUID paidEntryId, OffsetDateTime paidAt) {
        this(id, planId, direction, vehicle, seq, count, dueDate, amountCents, method, noteNo, payer, counterparty, status, daysLate,
                paidEntryId, paidAt, null, null, null, false);
    }
}
