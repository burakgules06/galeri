package com.galeri.app.model.response;

import com.galeri.app.model.enums.AracDurum;
import com.galeri.app.model.enums.Parca;
import com.galeri.app.model.enums.ParcaDurum;
import com.galeri.app.model.enums.Vites;
import com.galeri.app.model.enums.Yakit;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

// Teknik bilgiler + hasar semasi. Para alani YOK (tramer hasar kaydi teknik bilgi sayilir).
// STAFF bunu alir; ADMIN bunu AracDetayAdmin icinde alir.
public record AracDetay(
        UUID id,
        String plaka,
        String marka,
        String model,
        short modelYili,
        int km,
        Yakit yakit,
        Vites vites,
        String renk,
        Long tramerKurus,
        String notlar,
        AracDurum durum,
        LocalDate alisTarihi,
        LocalDate satisTarihi,
        long stoktaGun,
        Map<Parca, ParcaDurum> parcalar
) {}
