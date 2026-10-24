package com.galeri.app.service;

import com.galeri.app.model.request.KisiRequest;
import com.galeri.app.model.response.KisiResponse;

import java.util.List;
import java.util.UUID;

public interface KisiService {
    List<KisiResponse> ara(String q);

    KisiResponse olustur(KisiRequest request);

    KisiResponse guncelle(UUID id, KisiRequest request);
}
