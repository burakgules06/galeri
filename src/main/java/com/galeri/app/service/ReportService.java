package com.galeri.app.service;

import com.galeri.app.model.response.MonthlyTrades;
import com.galeri.app.model.response.YearlyTrades;

public interface ReportService {
    /** ADMIN tum calisanlari, STAFF sadece kendini gorur. */
    MonthlyTrades monthlyTrades(int year, int month);

    /** Yilin tum alim ve satimlari. ADMIN herkesi, STAFF sadece kendi kayitlarini gorur. */
    YearlyTrades yearlyTrades(int year);
}
