package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.UUID;

// 1. adimda satis sadece tarih + alici + satan calisan + bedel olarak kaydedilir.
// Odeme plani ve tahsilat 3. adimda gelir.
public record AracSatisRequest(
        @NotNull LocalDate satisTarihi,
        UUID aliciId,
        UUID satanCalisanId,
        @PositiveOrZero Long satisKurus
) {}
