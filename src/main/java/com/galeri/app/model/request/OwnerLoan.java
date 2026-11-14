package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Parasi yetmeyen sermayedara eksik kadar dukkan sahibi borc verir (ayni islemde). accountId: borcun durdugu kasa / banka;
 * pocket: dukkan sahibi parayi cebinden koydu (once o hesaba sermaye girisi, sonra borc; kasadan / bankadan bir sey dusmez).
 */
public record OwnerLoan(@NotNull UUID accountId, boolean pocket) {}
