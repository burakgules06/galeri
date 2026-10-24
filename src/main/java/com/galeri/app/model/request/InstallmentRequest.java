package com.galeri.app.model.request;

import com.galeri.app.model.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

// Odeme planinin bir satiri. payerId bossa odeyen karsi taraftir (alici); INSTITUTION'da kurum zorunlu.
// paidNow: pesinat gibi o an alinan kisim, accountId (kasa / banka) ile ayni islemde tahsil edilir.
public record InstallmentRequest(
        @NotNull LocalDate dueDate,
        @NotNull @Positive Long amountCents,
        @NotNull PaymentMethod method,
        @Size(max = 40) String noteNo,
        UUID payerId,
        boolean paidNow,
        UUID accountId
) {}
