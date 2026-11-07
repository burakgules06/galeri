package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.enums.VehicleStatus;
import com.galeri.app.model.request.VehiclePartsRequest;
import com.galeri.app.model.request.VehicleRequest;
import com.galeri.app.model.request.BasketSaleRequest;
import com.galeri.app.model.request.VehicleSaleRequest;
import com.galeri.app.model.response.PaymentPlanResponse;
import com.galeri.app.model.response.VehicleDetailAdmin;
import com.galeri.app.model.response.VehicleSummary;
import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.service.VehicleService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Okuma herkese acik (STAFF'a para alanlari donmez), yazma sadece ADMIN.
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;
    private final PaymentPlanService paymentPlanService;
    private final GenericResponseService responseService;

    @GetMapping
    public GenericResponse<List<VehicleSummary>> search(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) VehicleStatus status) {
        return responseService.success(vehicleService.search(q, status));
    }

    @GetMapping("/{id}")
    public GenericResponse<Object> detail(@PathVariable UUID id) {
        return responseService.success(vehicleService.detail(id));
    }

    @PostMapping
    // sermayedar: kendi sermayesiyle (ortakli) alim; yetkili calisan: pesin alim (cevapta tutar yok)
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR') or @staffTrade.allowed()")
    public GenericResponse<Object> create(@Valid @RequestBody VehicleRequest request) {
        return responseService.success(forCaller(vehicleService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<VehicleDetailAdmin> update(@PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
        return responseService.success(vehicleService.update(id, request));
    }

    @PutMapping("/{id}/parts")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<VehicleDetailAdmin> updateParts(@PathVariable UUID id,
                                                          @Valid @RequestBody VehiclePartsRequest request) {
        return responseService.success(vehicleService.updateParts(id, request));
    }

    @PostMapping("/basket-sale")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<VehicleDetailAdmin> sellBasket(@Valid @RequestBody BasketSaleRequest request) {
        return responseService.success(vehicleService.sellBasket(request));
    }

    @PostMapping("/{id}/sale")
    // sermayedar: sadece kendi araci; yetkili calisan: pesin satis (cevapta tutar yok)
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR') or @staffTrade.allowed()")
    public GenericResponse<Object> sell(@PathVariable UUID id, @Valid @RequestBody VehicleSaleRequest request) {
        return responseService.success(forCaller(vehicleService.sell(id, request)));
    }

    // Calisana para alani donmez: girdigi tutari da sonra goremez (MIMARI, Yetki)
    private static Object forCaller(VehicleDetailAdmin admin) {
        return com.galeri.app.security.CurrentUser.isStaff() ? admin.vehicle() : admin;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<Void> remove(@PathVariable UUID id) {
        vehicleService.remove(id);
        return responseService.success(null);
    }

    @DeleteMapping("/{id}/sale")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<VehicleDetailAdmin> undoSale(@PathVariable UUID id) {
        return responseService.success(vehicleService.undoSale(id));
    }

    @GetMapping("/{id}/payment-plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR')")
    public GenericResponse<List<PaymentPlanResponse>> paymentPlans(@PathVariable UUID id) {
        vehicleService.requireAccess(id);
        return responseService.success(paymentPlanService.plans(id));
    }
}
