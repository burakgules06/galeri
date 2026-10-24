package com.galeri.app.model.response;

import java.util.UUID;

// Sadece ADMIN'e doner: teknik detay + kisiler + para alanlari (kurus).
public record AracDetayAdmin(
        AracDetay arac,
        KisiOzet satici,
        CalisanOzet alanCalisan,
        Long gercekAlisKurus,
        Long resmiAlisKurus,
        KisiOzet alici,
        CalisanOzet satanCalisan,
        Long satisKurus,
        UUID alisHesapId          // alis odemesinin cikdigi kasa/banka; deftere yazilmadiysa null
) {}
