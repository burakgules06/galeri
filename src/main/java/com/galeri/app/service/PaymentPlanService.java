package com.galeri.app.service;

import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.request.BasketSaleRequest;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.EndorseRequest;
import com.galeri.app.model.request.InstallmentRequest;
import com.galeri.app.model.request.InstallmentUpdateRequest;
import com.galeri.app.model.request.OpenItemsRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.ContactStatement;
import com.galeri.app.model.response.InstallmentResponse;
import com.galeri.app.model.response.PaymentPlanResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Odeme plani, taksitler, tahsilat ve cari ozeti (bkz. docs/MIMARI.md §6). Sadece ADMIN.
public interface PaymentPlanService {

    /**
     * Satis planini kurar ve deftere yazar (VehicleService.sell cagirir, ayni transaction):
     * her odeyen icin VEHICLE_SALE INCOME -> BALANCE(odeyen), "simdi alindi" taksitler icin COLLECTION.
     */
    void recordSale(Vehicle vehicle, Contact buyer, VehicleSaleRequest request);

    /**
     * Satisi geri alir: hic taksit odenmemis olmali; satis hareketleri terslenir, plan silinir. Arac bir sepetteyse sepetin
     * tamami (butun araclarin satis hareketleri) geri alinir.
     */
    void undoSale(Vehicle vehicle);

    /**
     * Toplu satis: tek plan (aracsiz) + sepet satirlari; satis hareketi arac basina (bedeller), taksitler toplam uzerinden.
     * Araclarin satis alanlarini cagiran (VehicleService) doldurur.
     */
    void recordBasketSale(List<Vehicle> vehicles, List<Long> prices, Contact buyer, BasketSaleRequest request);

    /** Arac bir sepetteyse sepetteki butun araclar (kendisi dahil), degilse bos. */
    List<UUID> basketVehicleIds(UUID vehicleId);

    /**
     * Aracin alis odemesini esitler (VehicleService create / update cagirir, ayni transaction).
     * plan bossa pesin akis: varsa alis plani kaldirilir (odenmis taksit yoksa) ve LedgerService.syncVehiclePurchase calisir.
     * plan doluysa planli alis: VEHICLE_PURCHASE BALANCE(satici) -> VEHICLE, "simdi odendi" satirlar PAYMENT ile kapanir.
     * Odenmis taksit varsa bedel ve satici degistirilemez; sadece odenmemis kisim yeniden yazilir.
     */
    void syncPurchase(Vehicle vehicle, UUID purchaseAccountId, List<InstallmentRequest> plan);

    List<PaymentPlanResponse> plans(UUID vehicleId);

    /** Taksit listesi. Parametreler null ise suzulmez; durum bugune (Clock) gore hesaplanir. */
    List<InstallmentResponse> list(InstallmentStatus status, PlanDirection direction, LocalDate from, LocalDate to);

    /** Taksitin tamamini tek seferde tahsil eder (satis) ya da oder (alis). Kismi tahsilat yok. */
    InstallmentResponse collect(UUID installmentId, CollectRequest request);

    /** Tahsilati geri alir: bagli hareket terslenir, taksit odenmemise doner. */
    InstallmentResponse undo(UUID installmentId);

    /** Odenmemis taksitin vadesini ve senet no'sunu degistirir. */
    InstallmentResponse update(UUID installmentId, InstallmentUpdateRequest request);

    /**
     * Ciro: alinmis, odenmemis cek / senet baska birine odeme olarak verilir. Taksit odenmis olur (portfoye), alan kisinin
     * carisine ya da secilen acik alis taksitine odeme yazilir. Taksit cekten buyukse bolunur.
     */
    InstallmentResponse endorse(UUID installmentId, EndorseRequest request);

    /**
     * Gecmis senet / cek aktarimi: kisinin carisine aracsiz plan. Toplam acilis hareketiyle cariye yazilir, taksitler acik girer
     * (tahsil / ode / ciro / karsiliksiz aracli planlardaki gibi).
     */
    PaymentPlanResponse addOpenItems(UUID contactId, OpenItemsRequest request);

    /** Aracsiz plani siler (hicbir taksiti odenmemisse): acilis hareketi terslenir. Hata yapan sifirdan girer. */
    void removeOpenItems(UUID planId);

    /** Karsiliksiz: tahsil ya da ciro edildiyse geri alinir, borc yeniden acilir, taksit karsiliksiz olarak isaretlenir. */
    InstallmentResponse bounce(UUID installmentId);

    ContactStatement statement(UUID contactId);
}
