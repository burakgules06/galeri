package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

// Borc verme ve geri odeme (V33). accountId: paranin durdugu kasa / banka. Borc vermede verenin o hesaptaki parasindan
// verilir; geri odemede borclunun o hesaptaki parasindan odenir.
public record LoanRequest(
        @NotNull UUID lenderUserId,
        @NotNull UUID borrowerUserId,
        @NotNull UUID accountId,
        @NotNull LocalDate date,
        @NotNull @Positive Long amountCents,
        @Size(max = 500) String description
) {}
