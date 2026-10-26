package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.service.CatalogService;
import com.galeri.app.service.impl.GenericResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Arac formundaki marka / model secicileri. Para icermez; oturum acmis herkes okuyabilir.
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final GenericResponseService responseService;

    @GetMapping("/makes")
    public GenericResponse<List<CatalogService.Item>> makes() {
        return responseService.success(catalogService.makes());
    }

    @GetMapping("/makes/{make}/models")
    public GenericResponse<List<CatalogService.Item>> models(@PathVariable String make) {
        return responseService.success(catalogService.models(make));
    }
}
