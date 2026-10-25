package com.galeri.app.service;

import com.galeri.app.model.entity.Contact;
import com.galeri.app.model.entity.LedgerEntry;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.ExpenseRoot;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.ContactPaymentRequest;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.response.AccountResponse;
import com.galeri.app.model.response.CategoryResponse;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.model.response.VehicleCost;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Para defteri (bkz. docs/MIMARI.md §3). Sadece ADMIN.
public interface LedgerService {

    /** Kasa ve banka hesaplari, bakiyeleriyle. Kasa yoksa olusturulur. */
    List<AccountResponse> moneyAccounts();

    AccountResponse addBank(AccountRequest request);

    /** accountId null ise galerinin tum hareketleri; yeniden eskiye. */
    List<LedgerEntryResponse> entries(UUID accountId);

    LedgerEntryResponse addExpense(ExpenseRequest request);

    /** Ayni tutarda ters yonde hareket atar; asil hareket silinmez. */
    LedgerEntryResponse correct(UUID entryId);

    List<CategoryResponse> categories(ExpenseRoot root);

    VehicleCost vehicleCost(UUID vehicleId);

    /**
     * Aracin pesin alis hareketini aracin alis bilgisine esitler (VehicleService cagirir, ayni transaction).
     * Tutar, tarih ya da hesap degistiyse eski hareket ters hareketle duzeltilir ve yenisi yazilir.
     */
    void syncVehiclePurchase(Vehicle vehicle, UUID purchaseAccountId);

    /** Aracin gecerli alis hareketinin kaynak hesabi; yoksa null. */
    UUID purchaseAccountId(UUID vehicleId);

    // ------------------------------------------------------------------ odeme plani (PaymentPlanService cagirir)

    /** Satis: INCOME -> BALANCE(odeyen). Odeyenin carisi yoksa acilir. */
    LedgerEntry writeSale(Vehicle vehicle, Contact payer, long amountCents, LocalDate date);

    /**
     * Taksit kapanisi. SALE: tahsilat (COLLECTION) BALANCE(odeyen) -> kasa/banka;
     * PURCHASE: odeme (PAYMENT) kasa/banka -> BALANCE(odeyen). accountId kasa ya da banka olmali.
     */
    LedgerEntry writeSettlement(PlanDirection direction, Vehicle vehicle, Contact payer, UUID accountId,
                                long amountCents, LocalDate date, String description);

    /** Hareketi ters hareketle duzeltir (bagli kayit kontrolu yapmadan; plan islemleri icin). */
    void reverse(UUID entryId);

    /** Aracin duzeltilmemis tum satis hareketlerini terser (satisi geri al). */
    void reverseVehicleSales(UUID vehicleId);

    /** Planli alis: BALANCE(odenecek kisi) -> VEHICLE. Odenecek kisinin carisi yoksa acilir. */
    LedgerEntry writePlannedPurchase(Vehicle vehicle, Contact payee, long amountCents, LocalDate date);

    /** Aracin gecerli (duzeltilmemis) tum alis hareketlerini terser: pesin ya da planli. */
    void reverseVehiclePurchases(UUID vehicleId);

    /** Aracin gecerli alis hareketleri (duzeltme ve duzeltilmis olanlar haric). */
    List<LedgerEntry> activeVehiclePurchases(UUID vehicleId);

    // ------------------------------------------------------------------ konsinye

    /**
     * Konsinye satisin sahibine borcunu aracin son haline esitler: satilmis konsinye aracta
     * BALANCE(sahibi) -> INCOME (satis - komisyon), satis tarihinde. Arac stoktaysa, konsinye degilse ya da
     * tutar/sahip/tarih degistiyse eski hareket terslenir. Satis, satisi geri alma ve arac guncellemesi cagirir.
     */
    void syncConsignmentSettlement(Vehicle vehicle);

    /** Kisiye odeme (ornegin konsinye sahibine): PAYMENT kasa/banka -> BALANCE(kisi). Kismi olabilir. */
    LedgerEntryResponse payContact(UUID contactId, ContactPaymentRequest request);
}
