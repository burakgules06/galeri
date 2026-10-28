package com.galeri.app.model.response;

import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PaymentMethod;
import com.galeri.app.model.enums.PlanDirection;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// Taksit satiri: plan ve arac bilgisiyle. Sadece ADMIN'e doner. seq / count: "2 / 5" gosterimi icin.
// downPayment / number / installmentCount: ekranda "Pesinat", "1. taksit" (pesinat sayilmaz).
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
        boolean bounced,
        boolean downPayment,       // plan kurulurken alinan / odenen pesinat
        int number,                // pesinat haric taksit no (pesinatta 0)
        int installmentCount,      // pesinat haric taksit sayisi
        ContactSummary holder,     // gecmis senet / cek sermayedarinsa (V18)
        long capitalCents,      // ortak alicinin bu taksitinde sermayeden karsilanacak kisim
        String description
) {
    public InstallmentResponse(UUID id, UUID planId, PlanDirection direction, VehicleRef vehicle, int seq, int count, LocalDate dueDate,
                               long amountCents, PaymentMethod method, String noteNo, ContactSummary payer, ContactSummary counterparty,
                               InstallmentStatus status, long daysLate, UUID paidEntryId, OffsetDateTime paidAt) {
        this(id, planId, direction, vehicle, seq, count, dueDate, amountCents, method, noteNo, payer, counterparty, status, daysLate,
                paidEntryId, paidAt, null, null, null, false, false, seq, count, null, 0L, null);
    }

    public InstallmentResponse(UUID id, UUID planId, PlanDirection direction, VehicleRef vehicle, int seq, int count, LocalDate dueDate,
                               long amountCents, PaymentMethod method, String noteNo, ContactSummary payer, ContactSummary counterparty,
                               InstallmentStatus status, long daysLate, UUID paidEntryId, OffsetDateTime paidAt,
                               String bankName, String drawerName, ContactSummary endorsedTo, boolean bounced,
                               boolean downPayment, int number, int installmentCount, ContactSummary holder, long capitalCents) {
        this(id, planId, direction, vehicle, seq, count, dueDate, amountCents, method, noteNo, payer, counterparty, status, daysLate,
                paidEntryId, paidAt, bankName, drawerName, endorsedTo, bounced, downPayment, number, installmentCount, holder, capitalCents, null);
    }
}
