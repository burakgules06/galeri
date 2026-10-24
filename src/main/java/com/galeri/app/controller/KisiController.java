package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.request.KisiRequest;
import com.galeri.app.model.response.KisiResponse;
import com.galeri.app.service.KisiService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Kisiler (musteri, satici, ortak, kurum) sadece galeri sahibine acik.
@RestController
@RequestMapping("/api/kisiler")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class KisiController {

    private final KisiService kisiService;
    private final GenericResponseService responseService;

    @GetMapping
    public GenericResponse<List<KisiResponse>> ara(@RequestParam(required = false) String q) {
        return responseService.success(kisiService.ara(q));
    }

    @PostMapping
    public GenericResponse<KisiResponse> olustur(@Valid @RequestBody KisiRequest request) {
        return responseService.success(kisiService.olustur(request));
    }

    @PutMapping("/{id}")
    public GenericResponse<KisiResponse> guncelle(@PathVariable UUID id, @Valid @RequestBody KisiRequest request) {
        return responseService.success(kisiService.guncelle(id, request));
    }
}
