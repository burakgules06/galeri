package com.galeri.app.service;

import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.enums.ExpenseRoot;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.response.AccountResponse;
import com.galeri.app.model.response.CategoryResponse;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.model.response.VehicleCost;

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
}
