package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.response.DashboardResponse;
import com.galeri.app.service.DashboardService;
import com.galeri.app.service.impl.GenericResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Ana ekran ozeti. Tamami para oldugu icin sadece ADMIN; STAFF ana ekrani sadece arac listesinden kurulur.
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final GenericResponseService responseService;

    @GetMapping
    public GenericResponse<DashboardResponse> summary() {
        return responseService.success(dashboardService.summary());
    }
}
