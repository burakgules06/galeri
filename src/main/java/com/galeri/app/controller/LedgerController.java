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
    private final GenericResponseService responseService;

    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR')")
    public GenericResponse<List<AccountResponse>> moneyAccounts() {
        if (com.galeri.app.security.CurrentUser.isInvestor()) {
            // Sermayedar: hesaplarda sadece kendi parasi (bakiye ve baskalarinin parasi donmez)
            return responseService.success(netWorthService.holdings().stream()
                    .map(h -> new AccountResponse(h.accountId(), h.type(), h.name(), h.balanceCents())).toList());
        }
        return responseService.success(ledgerService.moneyAccounts());
    }

    @PostMapping("/accounts")
    public GenericResponse<AccountResponse> addBank(@Valid @RequestBody AccountRequest request) {
        return responseService.success(ledgerService.addBank(request));
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
    public GenericResponse<LedgerEntryResponse> addExpense(@Valid @RequestBody ExpenseRequest request) {
        return responseService.success(ledgerService.addExpense(request));
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

    @GetMapping("/expense-categories")
    public GenericResponse<List<CategoryResponse>> categories(@RequestParam ExpenseRoot root) {
        return responseService.success(ledgerService.categories(root));
    }

    @GetMapping("/vehicles/{id}/cost")
    public GenericResponse<VehicleCost> vehicleCost(@PathVariable UUID id) {
        return responseService.success(ledgerService.vehicleCost(id));
    }
}
