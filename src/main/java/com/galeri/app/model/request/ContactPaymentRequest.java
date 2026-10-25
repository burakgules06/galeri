package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

// Kisiye odeme (ornegin konsinye sahibine satis parasi): kasa/bankadan kisinin carisine. Kismi olabilir.
public record ContactPaymentRequest(
        @NotNull UUID accountId,
        @NotNull LocalDate date,
        @NotNull @Positive Long amountCents,
        @Size(max = 500) String description
) {}
