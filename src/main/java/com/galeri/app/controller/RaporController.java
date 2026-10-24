package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.response.AylikAlimSatim;
import com.galeri.app.service.RaporService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/raporlar")
@RequiredArgsConstructor
public class RaporController {

    private final RaporService raporService;
    private final GenericResponseService responseService;

    // ADMIN tum calisanlari, STAFF sadece kendini gorur (filtre serviste).
    @GetMapping("/aylik-alim-satim")
    public GenericResponse<AylikAlimSatim> aylikAlimSatim(@RequestParam @Min(2000) @Max(2100) int yil,
                                                         @RequestParam @Min(1) @Max(12) int ay) {
        return responseService.success(raporService.aylikAlimSatim(yil, ay));
    }
}
