package com.galeri.app.model.enums;

// Degerler V3 migration'indaki ledger_entry_type_check kisitiyla birebir ayni olmali.
// Yeni tur eklemek icin yeni migration'da kisit yeniden tanimlanir.
public enum LedgerEntryType {
    VEHICLE_PURCHASE, VEHICLE_EXPENSE, GENERAL_EXPENSE, CAPITAL
}
