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
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<VehicleDetailAdmin> create(@Valid @RequestBody VehicleRequest request) {
        return responseService.success(vehicleService.create(request));
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
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<VehicleDetailAdmin> sell(@PathVariable UUID id, @Valid @RequestBody VehicleSaleRequest request) {
        return responseService.success(vehicleService.sell(id, request));
    }

    @DeleteMapping("/{id}/sale")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<VehicleDetailAdmin> undoSale(@PathVariable UUID id) {
        return responseService.success(vehicleService.undoSale(id));
    }

    @GetMapping("/{id}/payment-plans")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<List<PaymentPlanResponse>> paymentPlans(@PathVariable UUID id) {
        return responseService.success(paymentPlanService.plans(id));
    }
}
