package com.galeri.app.model.enums;

// Degerler ledger_entry_type_check kisitiyla (son tanim V10) birebir ayni olmali.
// Yeni tur eklemek icin yeni migration'da kisit yeniden tanimlanir.
// VEHICLE_SALE: INCOME -> BALANCE(alici), COLLECTION: BALANCE -> CASH/BANK, PAYMENT: CASH/BANK -> BALANCE.
// CONSIGNMENT_SETTLEMENT: konsinye satista BALANCE(sahibi) -> INCOME (satis - komisyon).
// CAPITAL: sermaye girisi CAPITAL(ortak) -> CASH/BANK, cekisi ters yonde. PROFIT_ALLOCATION: CAPITAL(ortak) -> INCOME
// (gelirdeki kar ortagin sermayesine gecer). TRANSFER: kasa <-> banka. OPENING_BALANCE: OPENING <-> kasa / banka / cari.
// VEHICLE_PROFIT_SHARE: ortak aracin satisinda ortagin kar payi CAPITAL(ortak) -> INCOME; zararda INCOME -> CAPITAL,
// katkiyi asan zarar INCOME -> BALANCE(ortak). CAPITAL_TRANSFER (V16): kisiler arasi sermaye aktarimi CAPITAL(alan) -> CAPITAL(veren).
public enum LedgerEntryType {
    VEHICLE_PURCHASE, VEHICLE_EXPENSE, GENERAL_EXPENSE, CAPITAL, VEHICLE_SALE, COLLECTION, PAYMENT, CONSIGNMENT_SETTLEMENT,
    PROFIT_ALLOCATION, TRANSFER, OPENING_BALANCE, VEHICLE_PROFIT_SHARE, CAPITAL_TRANSFER
}
