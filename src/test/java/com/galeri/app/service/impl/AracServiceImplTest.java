package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.AracZatenSatildiException;
import com.galeri.app.exception.runtime.GecersizTarihException;
import com.galeri.app.exception.runtime.PlakaStoktaVarException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Arac;
import com.galeri.app.model.entity.AracParca;
import com.galeri.app.model.enums.AracDurum;
import com.galeri.app.model.enums.Parca;
import com.galeri.app.model.enums.ParcaDurum;
import com.galeri.app.model.enums.Vites;
import com.galeri.app.model.enums.Yakit;
import com.galeri.app.model.request.AracRequest;
import com.galeri.app.model.request.AracSatisRequest;
import com.galeri.app.model.request.ParcalarRequest;
import com.galeri.app.model.response.AracDetay;
import com.galeri.app.model.response.AracDetayAdmin;
import com.galeri.app.model.response.AracOzet;
import com.galeri.app.repository.AracParcaRepository;
import com.galeri.app.repository.AracRepository;
import com.galeri.app.repository.KisiRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.service.DefterService;
import com.galeri.app.support.TestOturum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AracServiceImplTest {

    @Mock private AracRepository aracRepository;
    @Mock private AracParcaRepository aracParcaRepository;
    @Mock private KisiRepository kisiRepository;
    @Mock private PanelUserRepository panelUserRepository;
    @Mock private DefterService defterService;

    // "Su an": 1 Temmuz 2026, 09:00 Istanbul (06:00 UTC)
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T06:00:00Z"), ZoneOffset.UTC);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID kasaId = UUID.randomUUID();
    private AracServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AracServiceImpl(aracRepository, aracParcaRepository, kisiRepository, panelUserRepository, defterService, clock);
        TestOturum.ac(userId, tenantId, "ADMIN");
    }

    @AfterEach
    void tearDown() {
        TestOturum.kapat();
    }

    private AracRequest istek(String plaka) {
        return new AracRequest(plaka, "Renault", "Clio", (short) 2022, Yakit.BENZIN, Vites.MANUEL, 31000,
                "Kırmızı", null, null, LocalDate.of(2026, 6, 1), null, null, 72000000L, 60000000L, kasaId);
    }

    private Arac stoktaArac(UUID id, UUID tenant) {
        Arac a = new Arac();
        a.setId(id);
        a.setTenantId(tenant);
        a.setPlaka("34 KLM 4521");
        a.setMarka("Renault");
        a.setModel("Clio");
        a.setModelYili((short) 2022);
        a.setYakit(Yakit.BENZIN);
        a.setVites(Vites.MANUEL);
        a.setKm(31000);
        a.setAlisTarihi(LocalDate.of(2026, 6, 1));
        a.setGercekAlisKurus(72000000L);
        return a;
    }

    @Test
    void yeniArac_plakaNormalizeEdilip_kaydedilir() {
        when(aracRepository.existsByTenantIdAndPlakaAndDurum(tenantId, "34 KLM 4521", AracDurum.STOKTA)).thenReturn(false);
        when(aracRepository.save(any(Arac.class))).thenAnswer(inv -> {
            Arac a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AracDetayAdmin sonuc = service.olustur(istek("34klm4521"));

        ArgumentCaptor<Arac> kaydedilen = ArgumentCaptor.forClass(Arac.class);
        verify(aracRepository).save(kaydedilen.capture());
        assertThat(kaydedilen.getValue().getPlaka()).isEqualTo("34 KLM 4521");
        assertThat(kaydedilen.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(sonuc.arac().stoktaGun()).isEqualTo(30);
        assertThat(sonuc.gercekAlisKurus()).isEqualTo(72000000L);
        verify(defterService).aracAlisiniEsitle(kaydedilen.getValue(), kasaId);
    }

    @Test
    void aracGuncellenince_alisHareketiDeftereEsitlenir() {
        UUID id = UUID.randomUUID();
        Arac arac = stoktaArac(id, tenantId);
        when(aracRepository.findById(id)).thenReturn(Optional.of(arac));

        service.guncelle(id, istek("34 KLM 4521"));

        verify(defterService).aracAlisiniEsitle(arac, kasaId);
    }

    @Test
    void ayniPlakaStoktaVarsa_yeniArac_reddedilir() {
        when(aracRepository.existsByTenantIdAndPlakaAndDurum(tenantId, "34 KLM 4521", AracDurum.STOKTA)).thenReturn(true);

        assertThatThrownBy(() -> service.olustur(istek("34 KLM 4521"))).isInstanceOf(PlakaStoktaVarException.class);
        verify(aracRepository, never()).save(any());
    }

    @Test
    void satis_araciSatildiYapar_ikinciSatis_reddedilir() {
        UUID id = UUID.randomUUID();
        Arac arac = stoktaArac(id, tenantId);
        when(aracRepository.findById(id)).thenReturn(Optional.of(arac));

        AracDetayAdmin sonuc = service.sat(id, new AracSatisRequest(LocalDate.of(2026, 6, 20), null, null, 80000000L));

        assertThat(arac.getDurum()).isEqualTo(AracDurum.SATILDI);
        assertThat(sonuc.satisKurus()).isEqualTo(80000000L);
        assertThat(sonuc.arac().stoktaGun()).isEqualTo(19);
        assertThatThrownBy(() -> service.sat(id, new AracSatisRequest(LocalDate.of(2026, 6, 21), null, null, 1L)))
                .isInstanceOf(AracZatenSatildiException.class);
    }

    @Test
    void satisTarihi_alistanOnceyse_reddedilir() {
        UUID id = UUID.randomUUID();
        when(aracRepository.findById(id)).thenReturn(Optional.of(stoktaArac(id, tenantId)));

        assertThatThrownBy(() -> service.sat(id, new AracSatisRequest(LocalDate.of(2026, 5, 31), null, null, 1L)))
                .isInstanceOf(GecersizTarihException.class);
    }

    @Test
    void baskaGalerininAraci_bulunamadiGibiDavranir() {
        UUID id = UUID.randomUUID();
        when(aracRepository.findById(id)).thenReturn(Optional.of(stoktaArac(id, UUID.randomUUID())));

        assertThatThrownBy(() -> service.detay(id)).isInstanceOf(SourceNotBelongToTenantException.class);
    }

    @Test
    void calisan_detaydaParaAlanlariniGormez() {
        TestOturum.ac(userId, tenantId, "STAFF");
        UUID id = UUID.randomUUID();
        when(aracRepository.findById(id)).thenReturn(Optional.of(stoktaArac(id, tenantId)));

        Object sonuc = service.detay(id);

        assertThat(sonuc).isInstanceOf(AracDetay.class).isNotInstanceOf(AracDetayAdmin.class);
        assertThat(((AracDetay) sonuc).parcalar()).hasSize(Parca.values().length)
                .allSatisfy((p, d) -> assertThat(d).isEqualTo(ParcaDurum.ORIJINAL));
    }

    @Test
    void yonetici_detaydaParaAlanlariniGorur() {
        UUID id = UUID.randomUUID();
        when(aracRepository.findById(id)).thenReturn(Optional.of(stoktaArac(id, tenantId)));

        assertThat(service.detay(id)).isInstanceOf(AracDetayAdmin.class);
    }

    @Test
    void parcalar_orijinalOlanSilinir_digerleriEklenirYaDaGuncellenir() {
        UUID id = UUID.randomUUID();
        when(aracRepository.findById(id)).thenReturn(Optional.of(stoktaArac(id, tenantId)));
        AracParca boyaliKaput = parca(id, Parca.MOTOR_KAPUTU, ParcaDurum.BOYALI);
        AracParca degisenTampon = parca(id, Parca.ON_TAMPON, ParcaDurum.DEGISEN);
        when(aracParcaRepository.findByAracId(id)).thenReturn(List.of(boyaliKaput, degisenTampon));

        Map<Parca, ParcaDurum> istek = new EnumMap<>(Parca.class);
        istek.put(Parca.MOTOR_KAPUTU, ParcaDurum.DEGISEN);   // guncelle
        istek.put(Parca.ON_TAMPON, ParcaDurum.ORIJINAL);     // sil
        istek.put(Parca.TAVAN, ParcaDurum.LOKAL_BOYALI);     // ekle
        service.parcalariGuncelle(id, new ParcalarRequest(istek));

        assertThat(boyaliKaput.getDurum()).isEqualTo(ParcaDurum.DEGISEN);
        verify(aracParcaRepository).delete(degisenTampon);
        ArgumentCaptor<AracParca> eklenen = ArgumentCaptor.forClass(AracParca.class);
        verify(aracParcaRepository).save(eklenen.capture());
        assertThat(eklenen.getValue().getParca()).isEqualTo(Parca.TAVAN);
        assertThat(eklenen.getValue().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void liste_hasarOzetiVeStoktaGunHesaplanir() {
        UUID id = UUID.randomUUID();
        Arac arac = stoktaArac(id, tenantId);
        when(aracRepository.ara(tenantId, null, "")).thenReturn(List.of(arac));
        when(aracParcaRepository.findByAracIdIn(List.of(id))).thenReturn(List.of(
                parca(id, Parca.ON_TAMPON, ParcaDurum.BOYALI),
                parca(id, Parca.TAVAN, ParcaDurum.BOYALI),
                parca(id, Parca.MOTOR_KAPUTU, ParcaDurum.DEGISEN)));

        List<AracOzet> liste = service.ara(null, null);

        assertThat(liste).hasSize(1);
        assertThat(liste.get(0).hasar().boyali()).isEqualTo(2);
        assertThat(liste.get(0).hasar().degisen()).isEqualTo(1);
        assertThat(liste.get(0).hasar().lokalBoyali()).isZero();
        assertThat(liste.get(0).stoktaGun()).isEqualTo(30);
    }

    private static AracParca parca(UUID aracId, Parca p, ParcaDurum d) {
        AracParca ap = new AracParca();
        ap.setAracId(aracId);
        ap.setParca(p);
        ap.setDurum(d);
        return ap;
    }
}
