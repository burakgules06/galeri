package com.galeri.app.model.enums;

// Taksitin durumu, vadeye ve bugune (Clock) gore hesaplanir; veritabaninda saklanmaz.
// OVERDUE: odenmemis ve vadesi gecmis, UPCOMING: odenmemis, vadesi bugun ya da ileride.
public enum InstallmentStatus {
    PAID, OVERDUE, UPCOMING
}
