package com.galeri.app.model.response;

import com.galeri.app.model.enums.AracDurum;
import com.galeri.app.model.enums.Vites;
import com.galeri.app.model.enums.Yakit;

import java.time.LocalDate;
import java.util.UUID;

// Liste satiri. Para alani YOK; hem ADMIN hem STAFF'a ayni doner.
public record AracOzet(
        UUID id,
        String plaka,
        String marka,
        String model,
        short modelYili,
        int km,
        Yakit yakit,
        Vites vites,
        String renk,
        AracDurum durum,
        LocalDate alisTarihi,
        LocalDate satisTarihi,
        long stoktaGun,
        HasarOzeti hasar
) {}
