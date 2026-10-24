package com.galeri.app.model.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Calisan bazinda aylik alim-satim gorunumu. Tutar YOK.
public record AylikAlimSatim(int yil, int ay, List<CalisanAylik> calisanlar) {

    /** calisanId null ise "Belirtilmemis" grubu (araca calisan girilmemis). */
    public record CalisanAylik(UUID calisanId, String ad, List<AylikArac> alinanlar, List<AylikArac> satilanlar) {}

    public record AylikArac(UUID aracId, String plaka, String marka, String model, LocalDate tarih) {}
}
