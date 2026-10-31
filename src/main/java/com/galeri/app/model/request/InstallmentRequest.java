package com.galeri.app.model.request;

import com.galeri.app.model.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

// Odeme planinin bir satiri. payerId bossa odeyen karsi taraftir (alici); INSTITUTION'da kurum zorunlu.
// paidNow: pesinat gibi o an alinan kisim, accountId (kasa / banka) ile ayni islemde tahsil edilir.
// Cekte noteNo cek numarasi, bankName bankasi, drawerName keside eden (bos olabilir).
public record InstallmentRequest(
        @NotNull LocalDate dueDate,
        @NotNull @Positive Long amountCents,
        @NotNull PaymentMethod method,
        @Size(max = 40) String noteNo,
        UUID payerId,
        boolean paidNow,
        UUID accountId,
        @Size(max = 60) String bankName,
        @Size(max = 120) String drawerName,
        @Size(max = 500) String description
) {
    public InstallmentRequest(LocalDate dueDate, Long amountCents, PaymentMethod method, String noteNo, UUID payerId,
                              boolean paidNow, UUID accountId) {
        this(dueDate, amountCents, method, noteNo, payerId, paidNow, accountId, null, null, null);
    }

    public InstallmentRequest(LocalDate dueDate, Long amountCents, PaymentMethod method, String noteNo, UUID payerId,
                              boolean paidNow, UUID accountId, String bankName, String drawerName) {
        this(dueDate, amountCents, method, noteNo, payerId, paidNow, accountId, bankName, drawerName, null);
    }
}
