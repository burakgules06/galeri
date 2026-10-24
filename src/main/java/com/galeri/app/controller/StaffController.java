package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.response.StaffSummary;
import com.galeri.app.service.StaffService;
import com.galeri.app.service.impl.GenericResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Arac formundaki "alan / satan calisan" secimi icin.
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StaffController {

    private final StaffService staffService;
    private final GenericResponseService responseService;

    @GetMapping
    public GenericResponse<List<StaffSummary>> list() {
        return responseService.success(staffService.list());
    }
}
