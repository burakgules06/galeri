package com.galeri.app.service;

import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.request.LoanRequest;
import com.galeri.app.model.request.OwnerLoan;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.model.response.LoanBalance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Kisiler arasi borc (V33): dukkan sahibi ya da bir sermayedar baskasina borc verir, borclu geri oder. Para kasa / bankada
// kalir, sadece sermayeler ve o hesaptaki paylar el degistirir. Kalan borc hareketlerden hesaplanir.
public interface LoanService {

    /** Borc ver: LOAN CAPITAL(alan) -> CAPITAL(veren). Veren sermayedarsa o hesaptaki ve nakitteki parasini asamaz. */
    LedgerEntryResponse lend(LoanRequest request);

    /** Geri ode: LOAN_REPAYMENT CAPITAL(veren) -> CAPITAL(alan). Kalan borcu ve borclunun o hesaptaki parasini asamaz. */
    LedgerEntryResponse repay(LoanRequest request);

    /** Acik borclar (kalan > 0). Sermayedar sadece kendisinin taraf oldugu borclari gorur. */
    List<LoanBalance> balances();

    /**
     * Dukkan sahibi borrower'a amount kadar borc verir, loan.accountId'de. loan.pocket: once dukkan sahibinin o hesaba cepten
     * sermaye girisi yazilir (kasadan / bankadan bir sey dusmez), sonra borc. Cagiranin transaction'inda: islem hata verirse
     * borc da geri alinir.
     */
    void lendFromOwner(UUID borrowerUserId, long amountCents, OwnerLoan loan, LocalDate date, String description);

    /** Sermaye girisi / cekisi; cekiste sermayedarin nakdi yetmezse ve ownerLoan doluysa eksik kadar borc, sonra cekis (tek islem). */
    LedgerEntryResponse move(UUID userId, CapitalRequests.Movement request);

    /** Kisiler arasi sermaye aktarimi; gonderenin nakdi yetmezse ve ownerLoan doluysa eksik kadar borc, sonra aktarim (tek islem). */
    LedgerEntryResponse transferCapital(CapitalRequests.CapitalTransfer request);
}
