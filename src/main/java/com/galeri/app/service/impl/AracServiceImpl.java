package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.AracZatenSatildiException;
import com.galeri.app.exception.runtime.GecersizTarihException;
import com.galeri.app.exception.runtime.KayitBulunamadiException;
import com.galeri.app.exception.runtime.PlakaStoktaVarException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Arac;
import com.galeri.app.model.entity.AracParca;
import com.galeri.app.model.entity.Kisi;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.enums.AracDurum;
import com.galeri.app.model.enums.Parca;
import com.galeri.app.model.enums.ParcaDurum;
import com.galeri.app.model.request.AracRequest;
import com.galeri.app.model.request.AracSatisRequest;
import com.galeri.app.model.request.ParcalarRequest;
import com.galeri.app.model.response.AracDetay;
import com.galeri.app.model.response.AracDetayAdmin;
import com.galeri.app.model.response.AracOzet;
import com.galeri.app.model.response.CalisanOzet;
import com.galeri.app.model.response.HasarOzeti;
import com.galeri.app.model.response.KisiOzet;
import com.galeri.app.repository.AracParcaRepository;
import com.galeri.app.repository.AracRepository;
import com.galeri.app.repository.KisiRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.AracService;
import com.galeri.app.service.DefterService;
import com.galeri.app.util.PlakaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AracServiceImpl implements AracService {

    // Tek galeri Turkiye'de; tenant.timezone ileride kullanilabilir.
    static final ZoneId GALERI_SAAT_DILIMI = ZoneId.of("Europe/Istanbul");

    private final AracRepository aracRepository;
    private final AracParcaRepository aracParcaRepository;
    private final KisiRepository kisiRepository;
    private final PanelUserRepository panelUserRepository;
    private final DefterService defterService;
    private final Clock clock;

    // ------------------------------------------------------------------ okuma

    @Override
    @Transactional(readOnly = true)
    public List<AracOzet> ara(String q, AracDurum durum) {
        String arama = q == null ? "" : q.trim();
        List<Arac> araclar = aracRepository.ara(CurrentUser.tenantId(), durum, arama);
        if (araclar.isEmpty()) return List.of();

        Map<UUID, List<AracParca>> parcalar = aracParcaRepository
                .findByAracIdIn(araclar.stream().map(Arac::getId).toList()).stream()
                .collect(Collectors.groupingBy(AracParca::getAracId));

        LocalDate bugun = bugun();
        return araclar.stream()
                .map(a -> toOzet(a, parcalar.getOrDefault(a.getId(), List.of()), bugun))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Object detay(UUID id) {
        Arac arac = aracGetir(id);
        AracDetay detay = toDetay(arac);
        return CurrentUser.isAdmin() ? toAdmin(arac, detay) : detay;
    }

    // ------------------------------------------------------------------ yazma

    @Override
    @Transactional
    public AracDetayAdmin olustur(AracRequest request) {
        UUID tenantId = CurrentUser.tenantId();
        String plaka = PlakaUtil.normalize(request.plaka());
        if (aracRepository.existsByTenantIdAndPlakaAndDurum(tenantId, plaka, AracDurum.STOKTA)) {
            throw new PlakaStoktaVarException();
        }
        Arac arac = new Arac();
        arac.setTenantId(tenantId);
        arac.setPlaka(plaka);
        uygula(arac, request);
        Arac kayitli = aracRepository.save(arac);
        defterService.aracAlisiniEsitle(kayitli, request.alisHesapId());
        return toAdmin(kayitli, toDetay(kayitli));
    }

    @Override
    @Transactional
    public AracDetayAdmin guncelle(UUID id, AracRequest request) {
        Arac arac = aracGetir(id);
        String plaka = PlakaUtil.normalize(request.plaka());
        boolean plakaDegisti = !plaka.equals(arac.getPlaka());
        if (plakaDegisti && arac.getDurum() == AracDurum.STOKTA
                && aracRepository.existsByTenantIdAndPlakaAndDurum(arac.getTenantId(), plaka, AracDurum.STOKTA)) {
            throw new PlakaStoktaVarException();
        }
        arac.setPlaka(plaka);
        uygula(arac, request);
        if (arac.getSatisTarihi() != null && arac.getSatisTarihi().isBefore(arac.getAlisTarihi())) {
            throw new GecersizTarihException();
        }
        defterService.aracAlisiniEsitle(arac, request.alisHesapId());
        return toAdmin(arac, toDetay(arac));
    }

    @Override
    @Transactional
    public AracDetayAdmin parcalariGuncelle(UUID id, ParcalarRequest request) {
        Arac arac = aracGetir(id);
        Map<Parca, AracParca> mevcut = aracParcaRepository.findByAracId(arac.getId()).stream()
                .collect(Collectors.toMap(AracParca::getParca, Function.identity()));

        // Yerinde guncelleme: sil + ekle yapilmaz (Hibernate insert'leri delete'lerden once
        // calistirdigi icin (arac_id, parca) tekillik kisiti patlar).
        request.parcalar().forEach((parca, durum) -> {
            AracParca kayit = mevcut.get(parca);
            if (durum == ParcaDurum.ORIJINAL) {
                if (kayit != null) aracParcaRepository.delete(kayit);
            } else if (kayit != null) {
                kayit.setDurum(durum);
            } else {
                AracParca yeni = new AracParca();
                yeni.setTenantId(arac.getTenantId());
                yeni.setAracId(arac.getId());
                yeni.setParca(parca);
                yeni.setDurum(durum);
                aracParcaRepository.save(yeni);
            }
        });
        aracParcaRepository.flush();
        return toAdmin(arac, toDetay(arac));
    }

    @Override
    @Transactional
    public AracDetayAdmin sat(UUID id, AracSatisRequest request) {
        Arac arac = aracGetir(id);
        if (arac.getDurum() != AracDurum.STOKTA) throw new AracZatenSatildiException();
        if (request.satisTarihi().isBefore(arac.getAlisTarihi())) throw new GecersizTarihException();

        arac.setDurum(AracDurum.SATILDI);
        arac.setSatisTarihi(request.satisTarihi());
        arac.setAlici(kisiGetir(request.aliciId(), arac.getTenantId()));
        arac.setSatanCalisan(calisanGetir(request.satanCalisanId(), arac.getTenantId()));
        arac.setSatisKurus(request.satisKurus());
        return toAdmin(arac, toDetay(arac));
    }

    // ------------------------------------------------------------------ yardimcilar

    private void uygula(Arac arac, AracRequest r) {
        arac.setMarka(r.marka().trim());
        arac.setModel(r.model().trim());
        arac.setModelYili(r.modelYili());
        arac.setYakit(r.yakit());
        arac.setVites(r.vites());
        arac.setKm(r.km());
        arac.setRenk(bosIseNull(r.renk()));
        arac.setTramerKurus(r.tramerKurus());
        arac.setNotlar(bosIseNull(r.notlar()));
        arac.setAlisTarihi(r.alisTarihi());
        arac.setSatici(kisiGetir(r.saticiId(), arac.getTenantId()));
        arac.setAlanCalisan(calisanGetir(r.alanCalisanId(), arac.getTenantId()));
        arac.setGercekAlisKurus(r.gercekAlisKurus());
        arac.setResmiAlisKurus(r.resmiAlisKurus());
    }

    private Arac aracGetir(UUID id) {
        Arac arac = aracRepository.findById(id).orElseThrow(KayitBulunamadiException::new);
        if (!arac.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        return arac;
    }

    private Kisi kisiGetir(UUID id, UUID tenantId) {
        if (id == null) return null;
        Kisi kisi = kisiRepository.findById(id).orElseThrow(KayitBulunamadiException::new);
        if (!kisi.getTenantId().equals(tenantId)) throw new SourceNotBelongToTenantException();
        return kisi;
    }

    private PanelUser calisanGetir(UUID id, UUID tenantId) {
        if (id == null) return null;
        PanelUser calisan = panelUserRepository.findById(id).orElseThrow(KayitBulunamadiException::new);
        if (calisan.getTenant() == null || !calisan.getTenant().getId().equals(tenantId)) {
            throw new SourceNotBelongToTenantException();
        }
        return calisan;
    }

    private LocalDate bugun() {
        return LocalDate.now(clock.withZone(GALERI_SAAT_DILIMI));
    }

    /** Stokta ise bugune kadar, satildiysa satis gunune kadar gecen gun. */
    static long stoktaGun(Arac arac, LocalDate bugun) {
        LocalDate bitis = arac.getSatisTarihi() != null ? arac.getSatisTarihi() : bugun;
        return Math.max(0, ChronoUnit.DAYS.between(arac.getAlisTarihi(), bitis));
    }

    private static String bosIseNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private AracOzet toOzet(Arac a, List<AracParca> parcalar, LocalDate bugun) {
        int lokal = 0, boyali = 0, degisen = 0;
        for (AracParca p : parcalar) {
            switch (p.getDurum()) {
                case LOKAL_BOYALI -> lokal++;
                case BOYALI -> boyali++;
                case DEGISEN -> degisen++;
                case ORIJINAL -> { }
            }
        }
        return new AracOzet(a.getId(), a.getPlaka(), a.getMarka(), a.getModel(), a.getModelYili(), a.getKm(),
                a.getYakit(), a.getVites(), a.getRenk(), a.getDurum(), a.getAlisTarihi(), a.getSatisTarihi(),
                stoktaGun(a, bugun), new HasarOzeti(lokal, boyali, degisen));
    }

    private AracDetay toDetay(Arac a) {
        Map<Parca, ParcaDurum> parcalar = new EnumMap<>(Parca.class);
        for (Parca p : Parca.values()) parcalar.put(p, ParcaDurum.ORIJINAL);
        if (a.getId() != null) {
            aracParcaRepository.findByAracId(a.getId()).forEach(p -> parcalar.put(p.getParca(), p.getDurum()));
        }
        return new AracDetay(a.getId(), a.getPlaka(), a.getMarka(), a.getModel(), a.getModelYili(), a.getKm(),
                a.getYakit(), a.getVites(), a.getRenk(), a.getTramerKurus(), a.getNotlar(), a.getDurum(),
                a.getAlisTarihi(), a.getSatisTarihi(), stoktaGun(a, bugun()), parcalar);
    }

    private AracDetayAdmin toAdmin(Arac a, AracDetay detay) {
        return new AracDetayAdmin(detay,
                kisiOzet(a.getSatici()), calisanOzet(a.getAlanCalisan()), a.getGercekAlisKurus(), a.getResmiAlisKurus(),
                kisiOzet(a.getAlici()), calisanOzet(a.getSatanCalisan()), a.getSatisKurus(),
                defterService.alisHesapId(a.getId()));
    }

    private static KisiOzet kisiOzet(Kisi k) {
        return k == null ? null : new KisiOzet(k.getId(), k.getAd(), k.getTelefon());
    }

    private static CalisanOzet calisanOzet(PanelUser u) {
        return u == null ? null : new CalisanOzet(u.getId(), u.getFullName());
    }
}
