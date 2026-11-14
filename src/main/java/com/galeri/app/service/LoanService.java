package com.galeri.app.service;

import com.galeri.app.model.request.LoanRequest;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.model.response.LoanBalance;

import java.util.List;

// Kisiler arasi borc (V33): dukkan sahibi ya da bir sermayedar baskasina borc verir, borclu geri oder. Para kasa / bankada
// kalir, sadece sermayeler ve o hesaptaki paylar el degistirir. Kalan borc hareketlerden hesaplanir.
public interface LoanService {

    /** Borc ver: LOAN CAPITAL(alan) -> CAPITAL(veren). Veren sermayedarsa o hesaptaki ve nakitteki parasini asamaz. */
    LedgerEntryResponse lend(LoanRequest request);

    /** Geri ode: LOAN_REPAYMENT CAPITAL(veren) -> CAPITAL(alan). Kalan borcu ve borclunun o hesaptaki parasini asamaz. */
    LedgerEntryResponse repay(LoanRequest request);

    /** Acik borclar (kalan > 0). Sermayedar sadece kendisinin taraf oldugu borclari gorur. */
    List<LoanBalance> balances();
}
