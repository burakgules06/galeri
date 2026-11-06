package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Gecmis cari kaydinda (arac disi senet / cek / acik hesap) odenmemis satirin tutar duzeltmesi. Kurus.
public record OpenItemAmountRequest(@NotNull @Positive Long amountCents) {}
