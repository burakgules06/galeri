package com.galeri.app.service;

import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.response.CapitalResponses;
import com.galeri.app.model.response.LedgerEntryResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Sermaye ortaklari, donem kari ve kar payini sermayeye ekleme (MIMARI §5). Sistem kar dagitmaz; sadece gosterir,
// ortaklarin karariyla elle girilen kar payi bir harekettir. Paylar her zaman CAPITAL bakiyelerinden hesaplanir.
public interface CapitalService {

    /** Dukkan sahibi ve sermayedar kullanicilar, sermayeleriyle (V22). */
    List<CapitalResponses.Partner> partners();

    /**
     * Ortagi kaldirir: nakitteki parasi, girislerini yaptigi hesaplara (en cok girilenden baslayarak) cekis olarak iade edilir,
     * kalan kasadan; sonra sermaye hesabi kapanir. Dukkan sahibi ve aracta parasi olan kaldirilamaz (1055).
     */
    void removePartner(UUID userId);

    /** Sermayedarin havuzdaki yuzdesinin (bilgi) tabanini degistirir. */
    CapitalResponses.Partner setBase(UUID userId, CapitalRequests.Base request);

    /**
     * Kullanicilar arasi sermaye aktarimi. Veren sermayedarsa en fazla nakitteki sermayesi kadar (araclardaki kismi arac satilinca
     * nakde doner); dukkan sahibinde sinir yok (panel uyarir).
     */
    LedgerEntryResponse transferCapital(CapitalRequests.CapitalTransfer request);

    /** Sermaye girisi (CAPITAL -> kasa/banka) ya da cekisi (ters yon, sermayeden fazla olamaz). */
    LedgerEntryResponse move(UUID userId, CapitalRequests.Movement request);

    /** Donem kari: iki tarih dahil. Satilan araclarin kari + komisyonlar - genel giderler; ortak paylari bilgi amacli. */
    CapitalResponses.PeriodProfit periodProfit(LocalDate from, LocalDate to);

    /** Kar payini sermayeye ekle: CAPITAL(ortak) -> INCOME, tutar elle. */
    LedgerEntryResponse allocate(CapitalRequests.Allocation request);
}
