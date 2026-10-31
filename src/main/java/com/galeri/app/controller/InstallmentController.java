package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.enums.InstallmentStatus;
import com.galeri.app.model.enums.PlanDirection;
import com.galeri.app.model.request.CollectRequest;
import com.galeri.app.model.request.EndorseRequest;
import com.galeri.app.model.request.InstallmentUpdateRequest;
import com.galeri.app.model.response.InstallmentResponse;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Taksitler: liste (geciken / yaklasan / odenen), tahsil et (kismi olabilir), geri al, vade ve senet / cek bilgisi duzenle,
// cek / senet ciro et ve karsiliksiz isaretle. Tamami para, sadece ADMIN.
@RestController
@RequestMapping("/api/installments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class InstallmentController {

    private final PaymentPlanService paymentPlanService;
    private final GenericResponseService responseService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR')")   // sermayedar: sadece parasi kendisinin olan planlar
    public GenericResponse<List<InstallmentResponse>> list(
            @RequestParam(required = false) InstallmentStatus status,
            @RequestParam(required = false) PlanDirection direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return responseService.success(paymentPlanService.list(status, direction, from, to));
    }

    @PostMapping("/{id}/collect")
    public GenericResponse<InstallmentResponse> collect(@PathVariable UUID id, @Valid @RequestBody CollectRequest request) {
        return responseService.success(paymentPlanService.collect(id, request));
    }

    @PostMapping("/{id}/undo")
    public GenericResponse<InstallmentResponse> undo(@PathVariable UUID id) {
        return responseService.success(paymentPlanService.undo(id));
    }

    @PostMapping("/{id}/endorse")
    public GenericResponse<InstallmentResponse> endorse(@PathVariable UUID id, @Valid @RequestBody EndorseRequest request) {
        return responseService.success(paymentPlanService.endorse(id, request));
    }

    @PostMapping("/{id}/bounce")
    public GenericResponse<InstallmentResponse> bounce(@PathVariable UUID id) {
        return responseService.success(paymentPlanService.bounce(id));
    }

    @PatchMapping("/{id}")
    public GenericResponse<InstallmentResponse> update(@PathVariable UUID id, @Valid @RequestBody InstallmentUpdateRequest request) {
        return responseService.success(paymentPlanService.update(id, request));
    }
}
