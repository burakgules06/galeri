package com.galeri.app.model.enums;

// Degerler ledger_entry_type_check kisitiyla (son tanim V4) birebir ayni olmali.
// Yeni tur eklemek icin yeni migration'da kisit yeniden tanimlanir.
// VEHICLE_SALE: INCOME -> BALANCE(alici), COLLECTION: BALANCE -> CASH/BANK, PAYMENT: CASH/BANK -> BALANCE.
public enum LedgerEntryType {
    VEHICLE_PURCHASE, VEHICLE_EXPENSE, GENERAL_EXPENSE, CAPITAL, VEHICLE_SALE, COLLECTION, PAYMENT
}
