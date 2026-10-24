package com.galeri.app.service;

import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.InstallmentUpdateRequest;
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

    /** Satisi geri alir: hic taksit odenmemis olmali; satis hareketleri terslenir, plan silinir. */
    void undoSale(Vehicle vehicle);

    List<PaymentPlanResponse> plans(UUID vehicleId);

    /** Taksit listesi. Parametreler null ise suzulmez; durum bugune (Clock) gore hesaplanir. */
    List<InstallmentResponse> list(InstallmentStatus status, PlanDirection direction, LocalDate from, LocalDate to);

    /** Taksitin tamamini tek seferde tahsil eder (satis) ya da oder (alis). Kismi tahsilat yok. */
    InstallmentResponse collect(UUID installmentId, CollectRequest request);

    /** Tahsilati geri alir: bagli hareket terslenir, taksit odenmemise doner. */
    InstallmentResponse undo(UUID installmentId);

    /** Odenmemis taksitin vadesini ve senet no'sunu degistirir. */
    InstallmentResponse update(UUID installmentId, InstallmentUpdateRequest request);

    ContactStatement statement(UUID contactId);
}
