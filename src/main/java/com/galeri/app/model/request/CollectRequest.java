package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

// Taksit tahsilati (ya da odemesi): parayi alan / veren kasa ya da banka hesabi ve tarih. Kismi tahsilat yok.
public record CollectRequest(@NotNull UUID accountId, @NotNull LocalDate date) {}
