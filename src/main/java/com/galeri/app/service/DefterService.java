package com.galeri.app.service;

import com.galeri.app.model.entity.Arac;
import com.galeri.app.model.enums.MasrafKok;
import com.galeri.app.model.request.HesapRequest;
import com.galeri.app.model.request.MasrafRequest;
import com.galeri.app.model.response.AracMaliyet;
import com.galeri.app.model.response.HareketResponse;
import com.galeri.app.model.response.HesapResponse;
import com.galeri.app.model.response.KategoriResponse;

import java.util.List;
import java.util.UUID;

// Para defteri (bkz. docs/MIMARI.md §3). Sadece ADMIN.
public interface DefterService {

    /** Kasa ve banka hesaplari, bakiyeleriyle. Kasa yoksa olusturulur. */
    List<HesapResponse> paraHesaplari();

    HesapResponse bankaEkle(HesapRequest request);

    /** hesapId null ise galerinin tum hareketleri; yeniden eskiye. */
    List<HareketResponse> hareketler(UUID hesapId);

    HareketResponse masrafEkle(MasrafRequest request);

    /** Ayni tutarda ters yonde hareket atar; asil hareket silinmez. */
    HareketResponse duzelt(UUID hareketId);

    List<KategoriResponse> kategoriler(MasrafKok kok);

    AracMaliyet aracMaliyeti(UUID aracId);

    /**
     * Aracin pesin alis hareketini aracin alis bilgisine esitler (AracService cagirir, ayni transaction).
     * Tutar, tarih ya da hesap degistiyse eski hareket ters hareketle duzeltilir ve yenisi yazilir.
     */
    void aracAlisiniEsitle(Arac arac, UUID alisHesapId);

    /** Aracin gecerli alis hareketinin kaynak hesabi; yoksa null. */
    UUID alisHesapId(UUID aracId);
}
