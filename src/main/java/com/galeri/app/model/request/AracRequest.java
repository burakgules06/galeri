package com.galeri.app.model.request;

import com.galeri.app.model.enums.Vites;
import com.galeri.app.model.enums.Yakit;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

// Tutarlar kurus cinsinden (1.250.000,50 TL = 125000050).
// alisHesapId bos ise alis odemesi deftere yazilmaz (ornegin senetli alis, 3. adim).
public record AracRequest(
        @NotBlank String plaka,
        @NotBlank @Size(max = 40) String marka,
        @NotBlank @Size(max = 60) String model,
        @NotNull @Min(1950) @Max(2100) Short modelYili,
        @NotNull Yakit yakit,
        @NotNull Vites vites,
        @NotNull @PositiveOrZero Integer km,
        @Size(max = 30) String renk,
        @PositiveOrZero Long tramerKurus,
        String notlar,
        @NotNull LocalDate alisTarihi,
        UUID saticiId,
        UUID alanCalisanId,
        @PositiveOrZero Long gercekAlisKurus,
        @PositiveOrZero Long resmiAlisKurus,
        UUID alisHesapId          // pesin alis: gercek alis bedeli bu kasa/banka hesabindan deftere yazilir
) {}
