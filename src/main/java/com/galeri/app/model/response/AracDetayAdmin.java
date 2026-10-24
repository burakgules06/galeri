package com.galeri.app.model.response;

// Sadece ADMIN'e doner: teknik detay + kisiler + para alanlari (kurus).
public record AracDetayAdmin(
        AracDetay arac,
        KisiOzet satici,
        CalisanOzet alanCalisan,
        Long gercekAlisKurus,
        Long resmiAlisKurus,
        KisiOzet alici,
        CalisanOzet satanCalisan,
        Long satisKurus
) {}
