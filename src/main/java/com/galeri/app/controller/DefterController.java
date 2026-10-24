package com.galeri.app.controller;

import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.enums.MasrafKok;
import com.galeri.app.model.request.HesapRequest;
import com.galeri.app.model.request.MasrafRequest;
import com.galeri.app.model.response.AracMaliyet;
import com.galeri.app.model.response.HareketResponse;
import com.galeri.app.model.response.HesapResponse;
import com.galeri.app.model.response.KategoriResponse;
import com.galeri.app.service.DefterService;
import com.galeri.app.service.impl.GenericResponseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Para defteri: kasa/banka, hareketler, masraflar, arac maliyeti. Tamami para oldugu icin sadece ADMIN.
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DefterController {

    private final DefterService defterService;
    private final GenericResponseService responseService;

    @GetMapping("/hesaplar")
    public GenericResponse<List<HesapResponse>> paraHesaplari() {
        return responseService.success(defterService.paraHesaplari());
    }

    @PostMapping("/hesaplar")
    public GenericResponse<HesapResponse> bankaEkle(@Valid @RequestBody HesapRequest request) {
        return responseService.success(defterService.bankaEkle(request));
    }

    @GetMapping("/hareketler")
    public GenericResponse<List<HareketResponse>> hareketler(@RequestParam(required = false) UUID hesapId) {
        return responseService.success(defterService.hareketler(hesapId));
    }

    @PostMapping("/hareketler/{id}/duzelt")
    public GenericResponse<HareketResponse> duzelt(@PathVariable UUID id) {
        return responseService.success(defterService.duzelt(id));
    }

    @PostMapping("/masraflar")
    public GenericResponse<HareketResponse> masrafEkle(@Valid @RequestBody MasrafRequest request) {
        return responseService.success(defterService.masrafEkle(request));
    }

    @GetMapping("/masraf-kategorileri")
    public GenericResponse<List<KategoriResponse>> kategoriler(@RequestParam MasrafKok kok) {
        return responseService.success(defterService.kategoriler(kok));
    }

    @GetMapping("/araclar/{id}/maliyet")
    public GenericResponse<AracMaliyet> aracMaliyeti(@PathVariable UUID id) {
        return responseService.success(defterService.aracMaliyeti(id));
    }
}
