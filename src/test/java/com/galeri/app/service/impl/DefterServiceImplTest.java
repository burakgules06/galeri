package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.DuzeltmeDuzeltilemezException;
import com.galeri.app.exception.runtime.GecersizHesapException;
import com.galeri.app.exception.runtime.GecersizKategoriException;
import com.galeri.app.exception.runtime.HareketZatenDuzeltildiException;
import com.galeri.app.exception.runtime.KategoriSeviyeSiniriException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Arac;
import com.galeri.app.model.entity.Hareket;
import com.galeri.app.model.entity.Hesap;
import com.galeri.app.model.entity.MasrafKategori;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.enums.AracDurum;
import com.galeri.app.model.enums.HareketTur;
import com.galeri.app.model.enums.HesapTur;
import com.galeri.app.model.enums.MasrafKok;
import com.galeri.app.model.request.MasrafRequest;
import com.galeri.app.model.response.AracMaliyet;
import com.galeri.app.model.response.HareketResponse;
import com.galeri.app.model.response.HesapResponse;
import com.galeri.app.repository.AracRepository;
import com.galeri.app.repository.HareketRepository;
import com.galeri.app.repository.HesapRepository;
import com.galeri.app.repository.MasrafKategoriRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.support.TestOturum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)   // ortak hazirliklar (save, getReferenceById) her testte kullanilmiyor
class DefterServiceImplTest {

    @Mock private HesapRepository hesapRepository;
    @Mock private HareketRepository hareketRepository;
    @Mock private MasrafKategoriRepository kategoriRepository;
    @Mock private AracRepository aracRepository;
    @Mock private PanelUserRepository panelUserRepository;

    private static final LocalDate GUN = LocalDate.of(2026, 7, 1);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private DefterServiceImpl service;

    private Hesap kasa;
    private Hesap gider;

    @BeforeEach
    void setUp() {
        service = new DefterServiceImpl(hesapRepository, hareketRepository, kategoriRepository, aracRepository, panelUserRepository);
        TestOturum.ac(userId, tenantId, "ADMIN");

        kasa = hesap(HesapTur.KASA, tenantId);
        gider = hesap(HesapTur.GIDER, tenantId);
        when(hesapRepository.findById(kasa.getId())).thenReturn(Optional.of(kasa));
        when(hesapRepository.findFirstByTenantIdAndTur(tenantId, HesapTur.GIDER)).thenReturn(Optional.of(gider));

        PanelUser ben = new PanelUser();
        ben.setId(userId);
        ben.setFullName("Demo Yonetici");
        when(panelUserRepository.getReferenceById(userId)).thenReturn(ben);

        when(hareketRepository.save(any(Hareket.class))).thenAnswer(inv -> {
            Hareket h = inv.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(hesapRepository.save(any(Hesap.class))).thenAnswer(inv -> {
            Hesap h = inv.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(kategoriRepository.save(any(MasrafKategori.class))).thenAnswer(inv -> {
            MasrafKategori k = inv.getArgument(0);
            k.setId(UUID.randomUUID());
            return k;
        });
    }

    @AfterEach
    void tearDown() {
        TestOturum.kapat();
    }

    // ------------------------------------------------------------------ masraf

    @Test
    void aracMasrafi_kasadanAracHesabinaYazilir_kategoriYoksaIlkHarfiBuyukOlusur() {
        Arac arac = arac();
        when(aracRepository.findById(arac.getId())).thenReturn(Optional.of(arac));
        when(hesapRepository.findByAracId(arac.getId())).thenReturn(Optional.empty());
        when(kategoriRepository.findFirstByTenantIdAndKokAndUstIdIsNullAndAdIgnoreCase(tenantId, MasrafKok.ARAC, "boya"))
                .thenReturn(Optional.empty());

        HareketResponse sonuc = service.masrafEkle(masraf(kasa.getId(), 500000L, arac.getId(), "  boya "));

        ArgumentCaptor<Hareket> yazilan = ArgumentCaptor.forClass(Hareket.class);
        verify(hareketRepository).save(yazilan.capture());
        Hareket h = yazilan.getValue();
        assertThat(h.getTur()).isEqualTo(HareketTur.ARAC_MASRAF);
        assertThat(h.getKaynakHesap()).isSameAs(kasa);
        assertThat(h.getHedefHesap().getTur()).isEqualTo(HesapTur.ARAC);
        assertThat(h.getHedefHesap().getAracId()).isEqualTo(arac.getId());
        assertThat(h.getTutarKurus()).isEqualTo(500000L);
        assertThat(h.getTenantId()).isEqualTo(tenantId);
        assertThat(h.getOlusturan().getId()).isEqualTo(userId);
        assertThat(h.getKategori().getAd()).isEqualTo("Boya");
        assertThat(h.getKategori().getKok()).isEqualTo(MasrafKok.ARAC);
        assertThat(sonuc.kategori()).isEqualTo("Boya");
        assertThat(sonuc.arac().plaka()).isEqualTo("34 KLM 4521");
    }

    @Test
    void varOlanKategoriAdi_harfFarkiylaYazilsaBile_tekrarOlusmaz() {
        MasrafKategori kira = kategori(MasrafKok.GENEL, "Kira", null);
        when(kategoriRepository.findFirstByTenantIdAndKokAndUstIdIsNullAndAdIgnoreCase(tenantId, MasrafKok.GENEL, "KİRA"))
                .thenReturn(Optional.of(kira));

        service.masrafEkle(masraf(kasa.getId(), 4000000L, null, "KİRA"));

        verify(kategoriRepository, never()).save(any());
        ArgumentCaptor<Hareket> yazilan = ArgumentCaptor.forClass(Hareket.class);
        verify(hareketRepository).save(yazilan.capture());
        assertThat(yazilan.getValue().getKategori()).isSameAs(kira);
    }

    @Test
    void genelGider_giderHesabinaYazilir() {
        when(kategoriRepository.findFirstByTenantIdAndKokAndUstIdIsNullAndAdIgnoreCase(any(), any(), any()))
                .thenReturn(Optional.empty());

        service.masrafEkle(masraf(kasa.getId(), 4000000L, null, "Kira"));

        ArgumentCaptor<Hareket> yazilan = ArgumentCaptor.forClass(Hareket.class);
        verify(hareketRepository).save(yazilan.capture());
        assertThat(yazilan.getValue().getTur()).isEqualTo(HareketTur.GENEL_GIDER);
        assertThat(yazilan.getValue().getHedefHesap()).isSameAs(gider);
        assertThat(yazilan.getValue().getArac()).isNull();
    }

    @Test
    void masraf_kasaYaDaBankaDisindakiHesaptanOdenemez() {
        Hesap cari = hesap(HesapTur.CARI, tenantId);
        when(hesapRepository.findById(cari.getId())).thenReturn(Optional.of(cari));

        assertThatThrownBy(() -> service.masrafEkle(masraf(cari.getId(), 100L, null, "Kira")))
                .isInstanceOf(GecersizHesapException.class);
        verify(hareketRepository, never()).save(any());
    }

    @Test
    void baskaGalerininKasasi_bulunamadiGibiDavranir() {
        Hesap baskaKasa = hesap(HesapTur.KASA, UUID.randomUUID());
        when(hesapRepository.findById(baskaKasa.getId())).thenReturn(Optional.of(baskaKasa));

        assertThatThrownBy(() -> service.masrafEkle(masraf(baskaKasa.getId(), 100L, null, "Kira")))
                .isInstanceOf(SourceNotBelongToTenantException.class);
    }

    @Test
    void kategorisizMasraf_reddedilir() {
        assertThatThrownBy(() -> service.masrafEkle(masraf(kasa.getId(), 100L, null, "  ")))
                .isInstanceOf(GecersizKategoriException.class);
    }

    @Test
    void genelGiderKategorisi_aracMasrafindaKullanilamaz() {
        Arac arac = arac();
        when(aracRepository.findById(arac.getId())).thenReturn(Optional.of(arac));
        when(hesapRepository.findByAracId(arac.getId())).thenReturn(Optional.of(aracHesabi(arac)));
        MasrafKategori kira = kategori(MasrafKok.GENEL, "Kira", null);
        when(kategoriRepository.findById(kira.getId())).thenReturn(Optional.of(kira));

        MasrafRequest istek = new MasrafRequest(GUN, kasa.getId(), 100L, arac.getId(), kira.getId(), null, null, null);
        assertThatThrownBy(() -> service.masrafEkle(istek)).isInstanceOf(GecersizKategoriException.class);
    }

    @Test
    void kategori_dorduncuSeviyeyeInmez() {
        MasrafKategori k1 = kategori(MasrafKok.ARAC, "Bakım", null);
        MasrafKategori k2 = kategori(MasrafKok.ARAC, "Motor", k1.getId());
        MasrafKategori k3 = kategori(MasrafKok.ARAC, "Yağ", k2.getId());
        when(kategoriRepository.findById(k1.getId())).thenReturn(Optional.of(k1));
        when(kategoriRepository.findById(k2.getId())).thenReturn(Optional.of(k2));
        when(kategoriRepository.findById(k3.getId())).thenReturn(Optional.of(k3));
        Arac arac = arac();
        when(aracRepository.findById(arac.getId())).thenReturn(Optional.of(arac));
        when(hesapRepository.findByAracId(arac.getId())).thenReturn(Optional.of(aracHesabi(arac)));

        MasrafRequest istek = new MasrafRequest(GUN, kasa.getId(), 100L, arac.getId(), null, "Filtre", k3.getId(), null);
        assertThatThrownBy(() -> service.masrafEkle(istek)).isInstanceOf(KategoriSeviyeSiniriException.class);
    }

    // ------------------------------------------------------------------ duzeltme

    @Test
    void duzelt_tersYondeAyniTutardaHareketAtar() {
        Hareket asil = hareket(HareketTur.GENEL_GIDER, kasa, gider, 4000000L);
        when(hareketRepository.findById(asil.getId())).thenReturn(Optional.of(asil));
        when(hareketRepository.existsByDuzeltilenHareketId(asil.getId())).thenReturn(false);

        HareketResponse ters = service.duzelt(asil.getId());

        assertThat(ters.kaynak().id()).isEqualTo(gider.getId());
        assertThat(ters.hedef().id()).isEqualTo(kasa.getId());
        assertThat(ters.tutarKurus()).isEqualTo(4000000L);
        assertThat(ters.tarih()).isEqualTo(asil.getTarih());
        assertThat(ters.duzeltilenHareketId()).isEqualTo(asil.getId());
        verify(hareketRepository, never()).delete(any());
    }

    @Test
    void duzeltilmisHareket_tekrarDuzeltilemez() {
        Hareket asil = hareket(HareketTur.GENEL_GIDER, kasa, gider, 100L);
        when(hareketRepository.findById(asil.getId())).thenReturn(Optional.of(asil));
        when(hareketRepository.existsByDuzeltilenHareketId(asil.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.duzelt(asil.getId())).isInstanceOf(HareketZatenDuzeltildiException.class);
        verify(hareketRepository, never()).save(any());
    }

    @Test
    void duzeltmeHareketi_duzeltilemez() {
        Hareket ters = hareket(HareketTur.GENEL_GIDER, gider, kasa, 100L);
        ters.setDuzeltilenHareketId(UUID.randomUUID());
        when(hareketRepository.findById(ters.getId())).thenReturn(Optional.of(ters));

        assertThatThrownBy(() -> service.duzelt(ters.getId())).isInstanceOf(DuzeltmeDuzeltilemezException.class);
    }

    // ------------------------------------------------------------------ bakiye

    @Test
    void bakiye_girenEksiCikanHareketlerdir() {
        Hesap banka = hesap(HesapTur.BANKA, tenantId);
        when(hesapRepository.findFirstByTenantIdAndTur(tenantId, HesapTur.KASA)).thenReturn(Optional.of(kasa));
        when(hesapRepository.findByTenantIdAndTurInOrderByCreatedAtAsc(tenantId, List.of(HesapTur.KASA, HesapTur.BANKA)))
                .thenReturn(List.of(kasa, banka));
        when(hareketRepository.girenToplamlari(tenantId)).thenReturn(List.<Object[]>of(new Object[]{kasa.getId(), 500000000L}));
        when(hareketRepository.cikanToplamlari(tenantId)).thenReturn(List.<Object[]>of(
                new Object[]{kasa.getId(), 125000000L}, new Object[]{banka.getId(), 300000L}));

        List<HesapResponse> hesaplar = service.paraHesaplari();

        assertThat(hesaplar).extracting(HesapResponse::bakiyeKurus).containsExactly(375000000L, -300000L);
        verify(hesapRepository, never()).save(any());
    }

    @Test
    void kasaYoksa_ilkAcilistaOlusturulur() {
        when(hesapRepository.findFirstByTenantIdAndTur(tenantId, HesapTur.KASA)).thenReturn(Optional.empty());

        service.paraHesaplari();

        ArgumentCaptor<Hesap> yeni = ArgumentCaptor.forClass(Hesap.class);
        verify(hesapRepository).save(yeni.capture());
        assertThat(yeni.getValue().getTur()).isEqualTo(HesapTur.KASA);
        assertThat(yeni.getValue().getTenantId()).isEqualTo(tenantId);
    }

    // ------------------------------------------------------------------ arac alisi

    @Test
    void pesinAlis_kasadanAracHesabinaYazilir() {
        Arac arac = arac();
        when(hesapRepository.findByAracId(arac.getId())).thenReturn(Optional.empty());
        when(hareketRepository.findByArac_IdAndTur(arac.getId(), HareketTur.ARAC_ALIS)).thenReturn(List.of());

        service.aracAlisiniEsitle(arac, kasa.getId());

        ArgumentCaptor<Hareket> yazilan = ArgumentCaptor.forClass(Hareket.class);
        verify(hareketRepository).save(yazilan.capture());
        assertThat(yazilan.getValue().getTur()).isEqualTo(HareketTur.ARAC_ALIS);
        assertThat(yazilan.getValue().getKaynakHesap()).isSameAs(kasa);
        assertThat(yazilan.getValue().getTutarKurus()).isEqualTo(72000000L);
        assertThat(yazilan.getValue().getTarih()).isEqualTo(arac.getAlisTarihi());
    }

    @Test
    void alisHesabiSecilmediyse_deftereBirSeyYazilmaz() {
        Arac arac = arac();
        when(hareketRepository.findByArac_IdAndTur(arac.getId(), HareketTur.ARAC_ALIS)).thenReturn(List.of());

        service.aracAlisiniEsitle(arac, null);

        verify(hareketRepository, never()).save(any());
    }

    @Test
    void alisDegismediyse_yeniHareketYazilmaz() {
        Arac arac = arac();
        Hareket alis = hareket(HareketTur.ARAC_ALIS, kasa, aracHesabi(arac), 72000000L);
        alis.setTarih(arac.getAlisTarihi());
        when(hareketRepository.findByArac_IdAndTur(arac.getId(), HareketTur.ARAC_ALIS)).thenReturn(List.of(alis));
        when(hareketRepository.duzeltilenler(anyCollection())).thenReturn(List.of());

        service.aracAlisiniEsitle(arac, kasa.getId());

        verify(hareketRepository, never()).save(any());
    }

    @Test
    void alisBedeliDegisince_eskisiTerslenir_yenisiYazilir() {
        Arac arac = arac();
        Hesap aracHesabi = aracHesabi(arac);
        when(hesapRepository.findByAracId(arac.getId())).thenReturn(Optional.of(aracHesabi));
        Hareket eski = hareket(HareketTur.ARAC_ALIS, kasa, aracHesabi, 70000000L);
        eski.setTarih(arac.getAlisTarihi());
        when(hareketRepository.findByArac_IdAndTur(arac.getId(), HareketTur.ARAC_ALIS)).thenReturn(List.of(eski));
        when(hareketRepository.duzeltilenler(anyCollection())).thenReturn(List.of());

        service.aracAlisiniEsitle(arac, kasa.getId());   // arac.gercekAlisKurus = 72.000.000 kurus

        ArgumentCaptor<Hareket> yazilan = ArgumentCaptor.forClass(Hareket.class);
        verify(hareketRepository, times(2)).save(yazilan.capture());
        Hareket ters = yazilan.getAllValues().get(0);
        Hareket yeni = yazilan.getAllValues().get(1);
        assertThat(ters.getDuzeltilenHareketId()).isEqualTo(eski.getId());
        assertThat(ters.getKaynakHesap()).isSameAs(aracHesabi);
        assertThat(ters.getTutarKurus()).isEqualTo(70000000L);
        assertThat(yeni.getTutarKurus()).isEqualTo(72000000L);
        assertThat(yeni.getDuzeltilenHareketId()).isNull();
    }

    // ------------------------------------------------------------------ maliyet

    @Test
    void maliyet_alisArtiMasraflar_duzeltilenMasrafDuser_karHesaplanir() {
        Arac arac = arac();
        arac.setDurum(AracDurum.SATILDI);
        arac.setSatisKurus(80000000L);
        Hesap aracHesabi = aracHesabi(arac);
        when(aracRepository.findById(arac.getId())).thenReturn(Optional.of(arac));

        Hareket alis = hareket(HareketTur.ARAC_ALIS, kasa, aracHesabi, 72000000L);
        Hareket boya = hareket(HareketTur.ARAC_MASRAF, kasa, aracHesabi, 500000L);
        Hareket yanlis = hareket(HareketTur.ARAC_MASRAF, kasa, aracHesabi, 900000L);
        Hareket yanlisDuzeltme = hareket(HareketTur.ARAC_MASRAF, aracHesabi, kasa, 900000L);
        yanlisDuzeltme.setDuzeltilenHareketId(yanlis.getId());
        when(hareketRepository.aracHareketleri(arac.getId())).thenReturn(List.of(alis, boya, yanlis, yanlisDuzeltme));
        when(hareketRepository.findByArac_IdAndTur(arac.getId(), HareketTur.ARAC_ALIS)).thenReturn(List.of(alis));
        when(hareketRepository.duzeltilenler(anyCollection())).thenReturn(List.of(yanlis.getId()));

        AracMaliyet m = service.aracMaliyeti(arac.getId());

        assertThat(m.alisKurus()).isEqualTo(72000000L);
        assertThat(m.alisDefterde()).isTrue();
        assertThat(m.masrafKurus()).isEqualTo(500000L);
        assertThat(m.toplamKurus()).isEqualTo(72500000L);
        assertThat(m.karKurus()).isEqualTo(7500000L);
        assertThat(m.hareketler()).hasSize(4);
        assertThat(m.hareketler().get(2).duzeltildi()).isTrue();
    }

    @Test
    void stoktakiAracinKari_hesaplanmaz() {
        Arac arac = arac();
        when(aracRepository.findById(arac.getId())).thenReturn(Optional.of(arac));
        when(hareketRepository.aracHareketleri(arac.getId())).thenReturn(List.of());
        when(hareketRepository.findByArac_IdAndTur(arac.getId(), HareketTur.ARAC_ALIS)).thenReturn(List.of());

        AracMaliyet m = service.aracMaliyeti(arac.getId());

        assertThat(m.toplamKurus()).isEqualTo(72000000L);
        assertThat(m.alisDefterde()).isFalse();
        assertThat(m.karKurus()).isNull();
    }

    // ------------------------------------------------------------------ yardimcilar

    private MasrafRequest masraf(UUID kaynakId, long tutar, UUID aracId, String kategoriAd) {
        return new MasrafRequest(GUN, kaynakId, tutar, aracId, null, kategoriAd, null, null);
    }

    private Hesap hesap(HesapTur tur, UUID tenant) {
        Hesap h = new Hesap();
        h.setId(UUID.randomUUID());
        h.setTenantId(tenant);
        h.setTur(tur);
        h.setAd(tur.name());
        return h;
    }

    private Hesap aracHesabi(Arac arac) {
        Hesap h = hesap(HesapTur.ARAC, arac.getTenantId());
        h.setAracId(arac.getId());
        return h;
    }

    private Arac arac() {
        Arac a = new Arac();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setPlaka("34 KLM 4521");
        a.setMarka("Renault");
        a.setModel("Clio");
        a.setAlisTarihi(LocalDate.of(2026, 6, 1));
        a.setGercekAlisKurus(72000000L);
        return a;
    }

    private MasrafKategori kategori(MasrafKok kok, String ad, UUID ustId) {
        MasrafKategori k = new MasrafKategori();
        k.setId(UUID.randomUUID());
        k.setTenantId(tenantId);
        k.setKok(kok);
        k.setAd(ad);
        k.setUstId(ustId);
        return k;
    }

    private Hareket hareket(HareketTur tur, Hesap kaynak, Hesap hedef, long tutar) {
        Hareket h = new Hareket();
        h.setId(UUID.randomUUID());
        h.setTenantId(tenantId);
        h.setTur(tur);
        h.setKaynakHesap(kaynak);
        h.setHedefHesap(hedef);
        h.setTutarKurus(tutar);
        h.setTarih(GUN);
        return h;
    }
}
