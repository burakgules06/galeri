package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.response.MonthlyTrades;
import com.galeri.app.service.ReportService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final GenericResponseService responseService;

    // ADMIN tum calisanlari, STAFF sadece kendini gorur (filtre serviste).
    @GetMapping("/monthly-trades")
    public GenericResponse<MonthlyTrades> monthlyTrades(@RequestParam @Min(2000) @Max(2100) int year,
                                                       @RequestParam @Min(1) @Max(12) int month) {
        return responseService.success(reportService.monthlyTrades(year, month));
    }
}
