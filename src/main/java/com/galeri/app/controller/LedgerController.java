package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.enums.ExpenseRoot;
import com.galeri.app.model.request.AccountRequest;
import com.galeri.app.model.request.BulkExpenseRequest;
import com.galeri.app.model.request.ExpenseRequest;
import com.galeri.app.model.response.AccountResponse;
import com.galeri.app.model.response.CategoryResponse;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.model.response.VehicleCost;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Para defteri: kasa/banka, hareketler, masraflar, arac maliyeti. Tamami para oldugu icin sadece ADMIN.
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LedgerController {

    private final LedgerService ledgerService;
    private final com.galeri.app.service.NetWorthService netWorthService;
    private final com.galeri.app.service.VehicleService vehicleService;
    private final GenericResponseService responseService;

    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR') or @staffTrade.allowed() or @staffTrade.expenseAllowed()")
    public GenericResponse<List<AccountResponse>> moneyAccounts() {
        if (com.galeri.app.security.CurrentUser.isStaff()) {
            // Yetkili calisan: pesin alim / satimda ve masrafta hesap secer; sadece ad ve tur, bakiye asla donmez (0)
            return responseService.success(ledgerService.moneyAccounts().stream()
                    .map(a -> new AccountResponse(a.id(), a.type(), a.name(), 0)).toList());
        }
        if (com.galeri.app.security.CurrentUser.isInvestor()) {
            // Sermayedar: hesaplarda sadece kendi parasi (bakiye ve baskalarinin parasi donmez)
            return responseService.success(netWorthService.holdings().accounts().stream()
                    .map(h -> new AccountResponse(h.accountId(), h.type(), h.name(), h.balanceCents())).toList());
        }
        return responseService.success(ledgerService.moneyAccounts());
    }

    @PostMapping("/accounts")
    public GenericResponse<AccountResponse> addBank(@Valid @RequestBody AccountRequest request) {
        return responseService.success(ledgerService.addBank(request));
    }

    @PutMapping("/accounts/{id}")
    public GenericResponse<AccountResponse> renameBank(@PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
        return responseService.success(ledgerService.renameBank(id, request));
    }

    @DeleteMapping("/accounts/{id}")
    public GenericResponse<Void> removeBank(@PathVariable UUID id) {
        ledgerService.removeBank(id);
        return responseService.success(null);
    }

    @GetMapping("/ledger-entries")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR')")   // sermayedar: sadece kendi sermaye ve cari hareketleri
    public GenericResponse<List<LedgerEntryResponse>> entries(@RequestParam(required = false) UUID accountId) {
        return responseService.success(ledgerService.entries(accountId));
    }

    @PostMapping("/ledger-entries/{id}/correct")
    public GenericResponse<LedgerEntryResponse> correct(@PathVariable UUID id) {
        return responseService.success(ledgerService.correct(id));
    }

    @GetMapping("/expenses")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR')")   // sermayedar: sadece sahibi oldugu araclarin masraflari
    public GenericResponse<List<LedgerEntryResponse>> expenses(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return responseService.success(ledgerService.expenses(from == null ? java.time.LocalDate.of(2000, 1, 1) : from,
                to == null ? java.time.LocalDate.of(2100, 12, 31) : to));
    }

    @PostMapping("/expenses")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR') or @staffTrade.expenseAllowed()")
    public GenericResponse<LedgerEntryResponse> addExpense(@Valid @RequestBody ExpenseRequest request) {
        if (com.galeri.app.security.CurrentUser.isStaff()) {
            // Yetkili calisan: sadece arac masrafi, secilen kasa / bankadan; cevapta tutar donmez
            ledgerService.addExpense(staffExpense(request));
            return responseService.success(null);
        }
        if (com.galeri.app.security.CurrentUser.isInvestor()) {
            // Sermayedar: sadece kendi aracina masraf; para galerinin kasasindan (hesap secmez)
            if (request.vehicleId() == null) throw new org.springframework.security.access.AccessDeniedException("Sadece arac masrafi");
            vehicleService.requireAccess(request.vehicleId());
            request = new ExpenseRequest(request.date(), ledgerService.cashAccountId(), request.amountCents(), request.vehicleId(),
                    request.categoryId(), request.categoryName(), request.parentCategoryId(), request.description(), request.confirmNegative(), null);
            // (konsinye aracinda masraf kendi sermayesinden duser; ortakli aracta maliyete girer)
        }
        return responseService.success(ledgerService.addExpense(request));
    }

    /** Calisan masrafi: arac zorunlu, kasa / banka zorunlu; cepten ve "eksiye dussun" onayi yok (yonetici girer). */
    private ExpenseRequest staffExpense(ExpenseRequest r) {
        if (r.vehicleId() == null) throw new org.springframework.security.access.AccessDeniedException("Calisan sadece arac masrafi girer");
        // Arac ve hesap tenant'a gore servis icinde dogrulanir (getVehicle / getMoneyAccount: sadece kasa / banka)
        if (r.sourceAccountId() == null) throw new org.springframework.security.access.AccessDeniedException("Calisan masrafi kasa / bankadan girer");
        return new ExpenseRequest(r.date(), r.sourceAccountId(), r.amountCents(), r.vehicleId(), r.categoryId(), r.categoryName(),
                r.parentCategoryId(), r.description(), false, null);
    }

    /** Diger kazanc (noter / kredi komisyonu, kira geliri ...): secilen kasa / bankaya giris. Sadece dukkan sahibi. */
    @PostMapping("/incomes")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<LedgerEntryResponse> addIncome(@Valid @RequestBody com.galeri.app.model.request.IncomeRequest request) {
        return responseService.success(ledgerService.addIncome(request));
    }

    @PostMapping("/expenses/bulk")
    public GenericResponse<List<LedgerEntryResponse>> addExpenses(@Valid @RequestBody BulkExpenseRequest request) {
        return responseService.success(ledgerService.addExpenses(request.items()));
    }

    @PutMapping("/expenses/{id}")
    public GenericResponse<LedgerEntryResponse> updateExpense(@PathVariable UUID id, @Valid @RequestBody ExpenseRequest request) {
        return responseService.success(ledgerService.updateExpense(id, request));
    }

    @DeleteMapping("/expenses/{id}")
    public GenericResponse<LedgerEntryResponse> deleteExpense(@PathVariable UUID id) {
        return responseService.success(ledgerService.deleteExpense(id));
    }

    // Konsinye araca cepten masraf (sadece takip): sahibi sermayedar kendi aracina da girebilir.
    @GetMapping("/vehicles/{id}/pocket-expenses")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR')")
    public GenericResponse<List<com.galeri.app.model.response.PocketExpenseResponse>> pocketExpenses(@PathVariable UUID id) {
        vehicleService.requireAccess(id);
        return responseService.success(ledgerService.pocketExpenses(id));
    }

    @PostMapping("/pocket-expenses")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR')")
    public GenericResponse<com.galeri.app.model.response.PocketExpenseResponse> addPocketExpense(@Valid @RequestBody ExpenseRequest request) {
        if (request.vehicleId() != null) vehicleService.requireAccess(request.vehicleId());
        return responseService.success(ledgerService.addPocketExpense(request));
    }

    @DeleteMapping("/pocket-expenses/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR')")
    public GenericResponse<UUID> deletePocketExpense(@PathVariable UUID id) {
        vehicleService.requireAccess(ledgerService.pocketExpenseVehicleId(id));
        return responseService.success(ledgerService.deletePocketExpense(id));
    }

    @GetMapping("/expense-categories")
    @PreAuthorize("hasRole('ADMIN') or @staffTrade.expenseAllowed()")
    public GenericResponse<List<CategoryResponse>> categories(@RequestParam ExpenseRoot root) {
        // Calisan sadece masraf kategorilerini gorur
        if (root == ExpenseRoot.INCOME && com.galeri.app.security.CurrentUser.isStaff()) {
            throw new org.springframework.security.access.AccessDeniedException("Kazanc kategorileri sadece yoneticide");
        }
        return responseService.success(ledgerService.categories(root));
    }

    @GetMapping("/vehicles/{id}/cost")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR')")
    public GenericResponse<VehicleCost> vehicleCost(@PathVariable UUID id) {
        vehicleService.requireAccess(id);
        return responseService.success(ledgerService.vehicleCost(id));
    }
}
