package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

// aracId doluysa arac masrafi (aracin maliyetine eklenir), bossa genel gider.
// Kategori: kategoriId ya da kategoriAd; ad ile gelirse yoksa o an olusturulur.
public record MasrafRequest(
        @NotNull LocalDate tarih,
        @NotNull UUID kaynakHesapId,
        @NotNull @Positive Long tutarKurus,
        UUID aracId,
        UUID kategoriId,
        @Size(max = 60) String kategoriAd,
        UUID ustKategoriId,
        @Size(max = 500) String aciklama
) {}
