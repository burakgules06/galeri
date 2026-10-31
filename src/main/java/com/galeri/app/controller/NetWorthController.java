package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.response.NetWorthResponse;
import com.galeri.app.service.NetWorthService;
import com.galeri.app.service.impl.GenericResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class NetWorthController {
    private final NetWorthService service;
    private final GenericResponseService responses;

    @GetMapping("/api/net-worth")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse<NetWorthResponse> summary() {
        return responses.success(service.summary());
    }

    @GetMapping("/api/holdings")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR')")   // sermayedar: sadece kendi payi
    public GenericResponse<java.util.List<com.galeri.app.model.response.AccountHoldings>> holdings() {
        return responses.success(service.holdings());
    }

    @GetMapping("/api/holders")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVESTOR')")   // sermayedar: sadece kendi satiri
    public GenericResponse<java.util.List<com.galeri.app.model.response.CapitalResponses.Holder>> holders() {
        return responses.success(service.holders());
    }
}
