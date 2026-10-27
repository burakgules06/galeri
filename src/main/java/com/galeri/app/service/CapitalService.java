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

    List<CapitalResponses.Partner> partners();

    /** Kisiyi ortak yapar (CAPITAL hesabi acilir, sermaye 0). Zaten ortaksa ayni kalir. */
    CapitalResponses.Partner addPartner(CapitalRequests.Partner request);

    /** Dukkan sahibini isaretler (galeri basina bir); kisi ortak olmali. */
    CapitalResponses.Partner setOwner(UUID contactId);

    /**
     * Ortagi kaldirir: nakitteki parasi, girislerini yaptigi hesaplara (en cok girilenden baslayarak) cekis olarak iade edilir,
     * kalan kasadan; sonra sermaye hesabi kapanir. Dukkan sahibi ve aracta parasi olan kaldirilamaz (1055).
     */
    void removePartner(UUID contactId);

    /** Sermayedarin havuzdaki yuzdesinin (bilgi) tabanini degistirir. */
    CapitalResponses.Partner setBase(UUID contactId, CapitalRequests.Base request);

    /**
     * Kisiler arasi sermaye aktarimi. Veren sermayedarsa en fazla nakitteki sermayesi kadar (araclardaki kismi arac satilinca
     * nakde doner); dukkan sahibinde sinir yok (panel uyarir).
     */
    LedgerEntryResponse transferCapital(CapitalRequests.CapitalTransfer request);

    /** Sermaye girisi (CAPITAL -> kasa/banka) ya da cekisi (ters yon, sermayeden fazla olamaz). */
    LedgerEntryResponse move(UUID contactId, CapitalRequests.Movement request);

    /** Donem kari: iki tarih dahil. Satilan araclarin kari + komisyonlar - genel giderler; ortak paylari bilgi amacli. */
    CapitalResponses.PeriodProfit periodProfit(LocalDate from, LocalDate to);

    /** Kar payini sermayeye ekle: CAPITAL(ortak) -> INCOME, tutar elle. */
    LedgerEntryResponse allocate(CapitalRequests.Allocation request);
}
