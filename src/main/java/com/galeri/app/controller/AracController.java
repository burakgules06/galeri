package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.enums.AracDurum;
import com.galeri.app.model.request.AracRequest;
import com.galeri.app.model.request.AracSatisRequest;
import com.galeri.app.model.request.ParcalarRequest;
import com.galeri.app.model.response.AracDetayAdmin;
import com.galeri.app.model.response.AracOzet;
import com.galeri.app.service.AracService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Okuma herkese acik (STAFF'a para alanlari donmez), yazma sadece ADMIN.
@RestController
@RequestMapping("/api/araclar")
@RequiredArgsConstructor
public class AracController {

    private final AracService aracService;
    private final GenericResponseService responseService;

    @GetMapping
    public GenericResponse<List<AracOzet>> ara(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) AracDurum durum) {
        return responseService.success(aracService.ara(q, durum));
    }

    @GetMapping("/{id}")
    public GenericResponse<Object> detay(@PathVariable UUID id) {
        return responseService.success(aracService.detay(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<AracDetayAdmin> olustur(@Valid @RequestBody AracRequest request) {
        return responseService.success(aracService.olustur(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<AracDetayAdmin> guncelle(@PathVariable UUID id, @Valid @RequestBody AracRequest request) {
        return responseService.success(aracService.guncelle(id, request));
    }

    @PutMapping("/{id}/parcalar")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<AracDetayAdmin> parcalariGuncelle(@PathVariable UUID id,
                                                            @Valid @RequestBody ParcalarRequest request) {
        return responseService.success(aracService.parcalariGuncelle(id, request));
    }

    @PostMapping("/{id}/satis")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<AracDetayAdmin> sat(@PathVariable UUID id, @Valid @RequestBody AracSatisRequest request) {
        return responseService.success(aracService.sat(id, request));
    }
}
