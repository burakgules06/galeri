package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.model.response.CapitalResponses;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Sermaye, donem kari ve kasa islemleri (Kasa altindaki ekranlar). Tamami para oldugu icin sadece ADMIN.
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CapitalController {

    private final CapitalService capitalService;
    private final LedgerService ledgerService;
    private final GenericResponseService responseService;

    @GetMapping("/partners")
    public GenericResponse<List<CapitalResponses.Partner>> partners() {
        return responseService.success(capitalService.partners());
    }

    @DeleteMapping("/partners/{userId}")
    public GenericResponse<Void> removePartner(@PathVariable UUID userId) {
        capitalService.removePartner(userId);
        return responseService.success(null);
    }

    @PutMapping("/partners/{userId}/base")
    public GenericResponse<CapitalResponses.Partner> setBase(@PathVariable UUID userId, @Valid @RequestBody CapitalRequests.Base request) {
        return responseService.success(capitalService.setBase(userId, request));
    }

    @PostMapping("/capital-transfers")
    public GenericResponse<LedgerEntryResponse> transferCapital(@Valid @RequestBody CapitalRequests.CapitalTransfer request) {
        return responseService.success(capitalService.transferCapital(request));
    }

    @PostMapping("/partners/{userId}/capital")
    public GenericResponse<LedgerEntryResponse> move(@PathVariable UUID userId, @Valid @RequestBody CapitalRequests.Movement request) {
        return responseService.success(capitalService.move(userId, request));
    }

    @GetMapping("/reports/period-profit")
    public GenericResponse<CapitalResponses.PeriodProfit> periodProfit(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return responseService.success(capitalService.periodProfit(from, to));
    }

    @PostMapping("/profit-allocations")
    public GenericResponse<LedgerEntryResponse> allocate(@Valid @RequestBody CapitalRequests.Allocation request) {
        return responseService.success(capitalService.allocate(request));
    }

    @PostMapping("/transfers")
    public GenericResponse<LedgerEntryResponse> transfer(@Valid @RequestBody CapitalRequests.Transfer request) {
        return responseService.success(ledgerService.transfer(request));
    }

    @PostMapping("/opening-balances")
    public GenericResponse<LedgerEntryResponse> openingBalance(@Valid @RequestBody CapitalRequests.Opening request) {
        return responseService.success(ledgerService.openingBalance(request));
    }
}
