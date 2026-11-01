package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

// Taksit tahsilati (ya da odemesi): parayi alan / veren kasa ya da banka hesabi ve tarih.
// amountCents bossa taksitin tamami; azsa kismi: taksit ikiye bolunur, bu kadari odenir, kalani ayni vadeyle acik kalir.
// cashToMe (sermayedar): "Nakit aldım": tahsilattan ona dusen pay cebine gecti (sermayesinden cekis); bossa "Sermayeme ekle".
public record CollectRequest(UUID accountId, @NotNull LocalDate date, @Positive Long amountCents, Boolean cashToMe) {
    public CollectRequest(UUID accountId, LocalDate date) {
        this(accountId, date, null, null);
    }

    public CollectRequest(UUID accountId, LocalDate date, Long amountCents) {
        this(accountId, date, amountCents, null);
    }
}
