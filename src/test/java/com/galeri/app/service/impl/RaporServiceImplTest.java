package com.galeri.app.service.impl;

import com.galeri.app.model.entity.Arac;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.response.AylikAlimSatim;
import com.galeri.app.model.response.AylikAlimSatim.CalisanAylik;
import com.galeri.app.repository.AracRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.support.TestOturum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaporServiceImplTest {

    @Mock private AracRepository aracRepository;
    @Mock private PanelUserRepository panelUserRepository;

    private final UUID tenantId = UUID.randomUUID();
    private final LocalDate bas = LocalDate.of(2026, 6, 1);
    private final LocalDate son = LocalDate.of(2026, 6, 30);

    private PanelUser ahmet;
    private PanelUser veli;
    private RaporServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RaporServiceImpl(aracRepository, panelUserRepository);
        ahmet = kullanici("Ahmet");
        veli = kullanici("Veli");
    }

    @AfterEach
    void tearDown() {
        TestOturum.kapat();
    }

    @Test
    void yonetici_tumCalisanlariVeBelirtilmemisleriGorur() {
        TestOturum.ac(UUID.randomUUID(), tenantId, "ADMIN");
        when(panelUserRepository.findByTenant_IdOrderByFullName(tenantId)).thenReturn(List.of(ahmet, veli));
        when(aracRepository.findByTenantIdAndAlisTarihiBetween(tenantId, bas, son)).thenReturn(List.of(
                arac("06 ABC 123", ahmet, LocalDate.of(2026, 6, 10), null, null),
                arac("34 KLM 45", null, LocalDate.of(2026, 6, 3), null, null)));
        when(aracRepository.findByTenantIdAndSatisTarihiBetween(tenantId, bas, son)).thenReturn(List.of(
                arac("06 TR 7788", null, LocalDate.of(2026, 5, 1), ahmet, LocalDate.of(2026, 6, 28))));

        AylikAlimSatim sonuc = service.aylikAlimSatim(2026, 6);

        assertThat(sonuc.calisanlar()).extracting(CalisanAylik::ad).containsExactly("Ahmet", "Veli", "Belirtilmemiş");
        CalisanAylik ahmetSatir = sonuc.calisanlar().get(0);
        assertThat(ahmetSatir.alinanlar()).extracting(a -> a.plaka()).containsExactly("06 ABC 123");
        assertThat(ahmetSatir.satilanlar()).extracting(a -> a.plaka()).containsExactly("06 TR 7788");
        assertThat(sonuc.calisanlar().get(1).alinanlar()).isEmpty();
        assertThat(sonuc.calisanlar().get(2).alinanlar()).extracting(a -> a.plaka()).containsExactly("34 KLM 45");
    }

    @Test
    void calisan_sadeceKendiAldigiVeSattigiAraclariGorur() {
        TestOturum.ac(ahmet.getId(), tenantId, "STAFF");
        when(panelUserRepository.findById(ahmet.getId())).thenReturn(Optional.of(ahmet));
        when(aracRepository.findByTenantIdAndAlisTarihiBetween(tenantId, bas, son)).thenReturn(List.of(
                arac("06 ABC 123", ahmet, LocalDate.of(2026, 6, 10), null, null),
                arac("35 VL 100", veli, LocalDate.of(2026, 6, 11), null, null),
                arac("34 KLM 45", null, LocalDate.of(2026, 6, 3), null, null)));
        when(aracRepository.findByTenantIdAndSatisTarihiBetween(tenantId, bas, son)).thenReturn(List.of());

        AylikAlimSatim sonuc = service.aylikAlimSatim(2026, 6);

        assertThat(sonuc.calisanlar()).hasSize(1);
        assertThat(sonuc.calisanlar().get(0).ad()).isEqualTo("Ahmet");
        assertThat(sonuc.calisanlar().get(0).alinanlar()).extracting(a -> a.plaka()).containsExactly("06 ABC 123");
    }

    private static PanelUser kullanici(String ad) {
        PanelUser u = new PanelUser();
        u.setId(UUID.randomUUID());
        u.setFullName(ad);
        u.setActive(true);
        return u;
    }

    private static Arac arac(String plaka, PanelUser calisan, LocalDate alis, PanelUser satan, LocalDate satis) {
        Arac a = new Arac();
        a.setId(UUID.randomUUID());
        a.setPlaka(plaka);
        a.setMarka("Marka");
        a.setModel("Model");
        a.setAlanCalisan(calisan);
        a.setAlisTarihi(alis);
        a.setSatanCalisan(satan);
        a.setSatisTarihi(satis);
        return a;
    }
}
