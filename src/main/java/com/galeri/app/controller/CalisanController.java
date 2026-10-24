package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.response.CalisanOzet;
import com.galeri.app.service.CalisanService;
import com.galeri.app.service.impl.GenericResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Arac formundaki "alan / satan calisan" secimi icin.
@RestController
@RequestMapping("/api/calisanlar")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CalisanController {

    private final CalisanService calisanService;
    private final GenericResponseService responseService;

    @GetMapping
    public GenericResponse<List<CalisanOzet>> listele() {
        return responseService.success(calisanService.listele());
    }
}
