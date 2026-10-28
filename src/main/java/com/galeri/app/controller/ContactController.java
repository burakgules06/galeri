package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.request.ContactRequest;
import com.galeri.app.model.request.OpenItemsRequest;
import com.galeri.app.model.response.PaymentPlanResponse;
import com.galeri.app.model.response.ContactResponse;
import com.galeri.app.model.response.ContactStatement;
import com.galeri.app.service.ContactService;
import com.galeri.app.model.request.ContactPaymentRequest;
import com.galeri.app.model.response.LedgerEntryResponse;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Kisiler (musteri, satici, ortak, kurum) sadece galeri sahibine acik.
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ContactController {

    private final ContactService contactService;
    private final PaymentPlanService paymentPlanService;
    private final LedgerService ledgerService;
    private final GenericResponseService responseService;

    @GetMapping
    public GenericResponse<List<ContactResponse>> search(@RequestParam(required = false) String q) {
        return responseService.success(contactService.search(q));
    }

    // Cari hesap ozeti: BALANCE hareketleri eskiden yeniye, yuruyen bakiye ve acik taksitler.
    @GetMapping("/{id}/statement")
    public GenericResponse<ContactStatement> statement(@PathVariable UUID id) {
        return responseService.success(paymentPlanService.statement(id));
    }

    // Kisiye odeme (konsinye sahibine satis parasi vb.): kasa/bankadan kisinin carisine, kismi olabilir.
    @PostMapping("/{id}/payments")
    public GenericResponse<LedgerEntryResponse> pay(@PathVariable UUID id, @Valid @RequestBody ContactPaymentRequest request) {
        return responseService.success(ledgerService.payContact(id, request));
    }

    @PostMapping("/{id}/open-items")
    public GenericResponse<PaymentPlanResponse> addOpenItems(@PathVariable UUID id, @Valid @RequestBody OpenItemsRequest request) {
        return responseService.success(paymentPlanService.addOpenItems(id, request));
    }

    @DeleteMapping("/open-items/{planId}")
    public GenericResponse<Void> removeOpenItems(@PathVariable UUID planId) {
        paymentPlanService.removeOpenItems(planId);
        return responseService.success(null);
    }

    @DeleteMapping("/open-items/{planId}/installments/{installmentId}")
    public GenericResponse<Void> removeOpenItemInstallment(@PathVariable UUID planId, @PathVariable UUID installmentId) {
        paymentPlanService.removeOpenItemInstallment(planId, installmentId);
        return responseService.success(null);
    }

    @PostMapping
    public GenericResponse<ContactResponse> create(@Valid @RequestBody ContactRequest request) {
        return responseService.success(contactService.create(request));
    }

    @PutMapping("/{id}")
    public GenericResponse<ContactResponse> update(@PathVariable UUID id, @Valid @RequestBody ContactRequest request) {
        return responseService.success(contactService.update(id, request));
    }
}
