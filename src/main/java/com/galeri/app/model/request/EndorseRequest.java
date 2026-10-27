package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

// Ciro: alinan cek / senet toContactId'ye odeme olarak verilir. targetInstallmentId doluysa o kisiye olan acik taksit
// (alis senedi gibi) bununla odenir; taksit cekten buyukse bolunur. Bossa kisinin carisine odeme yazilir.
public record EndorseRequest(@NotNull UUID toContactId, UUID targetInstallmentId, @NotNull LocalDate date) {}
