package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.KayitBulunamadiException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Kisi;
import com.galeri.app.model.enums.KisiTur;
import com.galeri.app.model.request.KisiRequest;
import com.galeri.app.model.response.KisiResponse;
import com.galeri.app.repository.KisiRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.KisiService;
import com.galeri.app.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KisiServiceImpl implements KisiService {

    private final KisiRepository kisiRepository;

    @Override
    @Transactional(readOnly = true)
    public List<KisiResponse> ara(String q) {
        String arama = q == null ? "" : q.trim();
        return kisiRepository.ara(CurrentUser.tenantId(), arama).stream().map(KisiServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional
    public KisiResponse olustur(KisiRequest request) {
        Kisi kisi = new Kisi();
        kisi.setTenantId(CurrentUser.tenantId());
        uygula(kisi, request);
        return toResponse(kisiRepository.save(kisi));
    }

    @Override
    @Transactional
    public KisiResponse guncelle(UUID id, KisiRequest request) {
        Kisi kisi = kisiRepository.findById(id).orElseThrow(KayitBulunamadiException::new);
        if (!kisi.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        uygula(kisi, request);
        return toResponse(kisi);
    }

    private static void uygula(Kisi kisi, KisiRequest request) {
        kisi.setAd(request.ad().trim());
        kisi.setTelefon(PhoneUtil.normalize(request.telefon()));
        kisi.setTur(request.tur() != null ? request.tur() : KisiTur.KISI);
        kisi.setNotlar(request.notlar());
    }

    static KisiResponse toResponse(Kisi k) {
        return new KisiResponse(k.getId(), k.getAd(), k.getTelefon(), k.getTur(), k.getNotlar());
    }
}
