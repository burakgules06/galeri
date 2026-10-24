package com.galeri.app.service;

import com.galeri.app.model.enums.AracDurum;
import com.galeri.app.model.request.AracRequest;
import com.galeri.app.model.request.AracSatisRequest;
import com.galeri.app.model.request.ParcalarRequest;
import com.galeri.app.model.response.AracDetayAdmin;
import com.galeri.app.model.response.AracOzet;

import java.util.List;
import java.util.UUID;

public interface AracService {
    List<AracOzet> ara(String q, AracDurum durum);

    /** ADMIN'e AracDetayAdmin, STAFF'a AracDetay (para alanlari olmadan) doner. */
    Object detay(UUID id);

    AracDetayAdmin olustur(AracRequest request);

    AracDetayAdmin guncelle(UUID id, AracRequest request);

    AracDetayAdmin parcalariGuncelle(UUID id, ParcalarRequest request);

    AracDetayAdmin sat(UUID id, AracSatisRequest request);
}
