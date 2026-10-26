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
}
