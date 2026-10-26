package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.enums.ExpenseRoot;
import com.galeri.app.model.request.AccountRequest;
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
    private final GenericResponseService responseService;

    @GetMapping("/accounts")
    public GenericResponse<List<AccountResponse>> moneyAccounts() {
        return responseService.success(ledgerService.moneyAccounts());
    }

    @PostMapping("/accounts")
    public GenericResponse<AccountResponse> addBank(@Valid @RequestBody AccountRequest request) {
        return responseService.success(ledgerService.addBank(request));
    }

    @GetMapping("/ledger-entries")
    public GenericResponse<List<LedgerEntryResponse>> entries(@RequestParam(required = false) UUID accountId) {
        return responseService.success(ledgerService.entries(accountId));
    }

    @PostMapping("/ledger-entries/{id}/correct")
    public GenericResponse<LedgerEntryResponse> correct(@PathVariable UUID id) {
        return responseService.success(ledgerService.correct(id));
    }

    @PostMapping("/expenses")
    public GenericResponse<LedgerEntryResponse> addExpense(@Valid @RequestBody ExpenseRequest request) {
        return responseService.success(ledgerService.addExpense(request));
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
