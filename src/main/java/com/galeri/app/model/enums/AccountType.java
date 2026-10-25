package com.galeri.app.model.enums;

// Degerler account_type_check kisitiyla (son tanim V8) birebir ayni olmali (bkz. docs/MIMARI.md §3).
// BALANCE: kisinin carisi, CAPITAL: ortagin sermayesi, VEHICLE: aracin maliyeti, OPENING: acilis bakiyelerinin karsi hesabi.
public enum AccountType {
    CASH, BANK, BALANCE, CAPITAL, VEHICLE, EXPENSE, INCOME, OPENING;

    /** Gercek para tutan hesaplar: masraf bunlardan odenir, bakiyesi ekranda gosterilir. */
    public boolean isMoneyAccount() {
        return this == CASH || this == BANK;
    }
}
