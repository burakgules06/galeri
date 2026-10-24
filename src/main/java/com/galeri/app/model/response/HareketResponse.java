package com.galeri.app.model.response;

import com.galeri.app.model.enums.HareketTur;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// duzeltilenHareketId doluysa bu bir duzeltme (ters) hareketidir; duzeltildi=true ise bu hareket iptal edilmistir.
public record HareketResponse(
        UUID id,
        LocalDate tarih,
        HareketTur tur,
        HesapOzet kaynak,
        HesapOzet hedef,
        long tutarKurus,
        AracKisa arac,
        String kategori,
        String aciklama,
        UUID duzeltilenHareketId,
        boolean duzeltildi,
        String olusturan,
        OffsetDateTime createdAt
) {}
