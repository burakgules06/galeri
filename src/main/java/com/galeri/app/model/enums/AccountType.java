package com.galeri.app.model.enums;

// Degerler V3 migration'indaki CHECK kisitiyla birebir ayni olmali (bkz. docs/MIMARI.md §3).
// BALANCE: kisinin carisi, CAPITAL: ortagin sermayesi, VEHICLE: aracin maliyeti.
public enum AccountType {
    CASH, BANK, BALANCE, CAPITAL, VEHICLE, EXPENSE, INCOME;

    /** Gercek para tutan hesaplar: masraf bunlardan odenir, bakiyesi ekranda gosterilir. */
    public boolean isMoneyAccount() {
        return this == CASH || this == BANK;
    }
}
