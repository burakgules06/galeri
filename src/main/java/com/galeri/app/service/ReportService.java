package com.galeri.app.service;

import com.galeri.app.model.response.MonthlyTrades;

public interface ReportService {
    /** ADMIN tum calisanlari, STAFF sadece kendini gorur. */
    MonthlyTrades monthlyTrades(int year, int month);
}
