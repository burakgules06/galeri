package com.galeri.app.model.enums;

// Degerler V3 migration'indaki CHECK kisitiyla birebir ayni olmali (bkz. docs/MIMARI.md §3).
public enum HesapTur {
    KASA, BANKA, CARI, SERMAYE, ARAC, GIDER, GELIR;

    /** Gercek para tutan hesaplar: masraf bunlardan odenir, bakiyesi ekranda gosterilir. */
    public boolean paraHesabi() {
        return this == KASA || this == BANKA;
    }
}
