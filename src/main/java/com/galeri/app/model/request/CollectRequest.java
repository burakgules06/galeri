package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

// Taksit tahsilati (ya da odemesi): parayi alan / veren kasa ya da banka hesabi ve tarih.
// amountCents bossa taksitin tamami; azsa kismi: taksit ikiye bolunur, bu kadari odenir, kalani ayni vadeyle acik kalir.
public record CollectRequest(@NotNull UUID accountId, @NotNull LocalDate date, @Positive Long amountCents) {
    public CollectRequest(UUID accountId, LocalDate date) {
        this(accountId, date, null);
    }
}
