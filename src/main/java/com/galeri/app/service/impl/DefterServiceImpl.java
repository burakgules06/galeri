package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.DuzeltmeDuzeltilemezException;
import com.galeri.app.exception.runtime.GecersizHesapException;
import com.galeri.app.exception.runtime.GecersizKategoriException;
import com.galeri.app.exception.runtime.HareketZatenDuzeltildiException;
import com.galeri.app.exception.runtime.KategoriSeviyeSiniriException;
import com.galeri.app.exception.runtime.KayitBulunamadiException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.model.entity.Arac;
import com.galeri.app.model.entity.Hareket;
import com.galeri.app.model.entity.Hesap;
import com.galeri.app.model.entity.MasrafKategori;
import com.galeri.app.model.enums.AracDurum;
import com.galeri.app.model.enums.HareketTur;
import com.galeri.app.model.enums.HesapTur;
import com.galeri.app.model.enums.MasrafKok;
import com.galeri.app.model.request.HesapRequest;
import com.galeri.app.model.request.MasrafRequest;
import com.galeri.app.model.response.AracKisa;
import com.galeri.app.model.response.AracMaliyet;
import com.galeri.app.model.response.HareketResponse;
import com.galeri.app.model.response.HesapOzet;
import com.galeri.app.model.response.HesapResponse;
import com.galeri.app.model.response.KategoriResponse;
import com.galeri.app.repository.AracRepository;
import com.galeri.app.repository.HareketRepository;
import com.galeri.app.repository.HesapRepository;
import com.galeri.app.repository.MasrafKategoriRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.DefterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefterServiceImpl implements DefterService {

    static final int LISTE_SINIRI = 300;
    static final int KATEGORI_EN_FAZLA_SEVIYE = 3;
    private static final Locale TURKCE = Locale.forLanguageTag("tr");

    private final HesapRepository hesapRepository;
    private final HareketRepository hareketRepository;
    private final MasrafKategoriRepository kategoriRepository;
    private final AracRepository aracRepository;
    private final PanelUserRepository panelUserRepository;

    // ------------------------------------------------------------------ hesaplar

    @Override
    @Transactional
    public List<HesapResponse> paraHesaplari() {
        UUID tenantId = CurrentUser.tenantId();
        sistemHesabi(tenantId, HesapTur.KASA);
        Map<UUID, Long> bakiyeler = bakiyeler(tenantId);
        return hesapRepository.findByTenantIdAndTurInOrderByCreatedAtAsc(tenantId, List.of(HesapTur.KASA, HesapTur.BANKA))
                .stream()
                .sorted(Comparator.comparing(Hesap::getTur))   // once kasa, sonra bankalar (eklenme sirasiyla)
                .map(h -> new HesapResponse(h.getId(), h.getTur(), h.getAd(), bakiyeler.getOrDefault(h.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public HesapResponse bankaEkle(HesapRequest request) {
        Hesap h = new Hesap();
        h.setTenantId(CurrentUser.tenantId());
        h.setTur(HesapTur.BANKA);
        h.setAd(request.ad().trim());
        Hesap kayitli = hesapRepository.save(h);
        return new HesapResponse(kayitli.getId(), kayitli.getTur(), kayitli.getAd(), 0L);
    }

    // ------------------------------------------------------------------ hareketler

    @Override
    @Transactional(readOnly = true)
    public List<HareketResponse> hareketler(UUID hesapId) {
        UUID tenantId = CurrentUser.tenantId();
        PageRequest sayfa = PageRequest.of(0, LISTE_SINIRI);
        List<Hareket> liste;
        if (hesapId == null) {
            liste = hareketRepository.liste(tenantId, sayfa);
        } else {
            hesapGetir(hesapId);
            liste = hareketRepository.hesapHareketleri(tenantId, hesapId, sayfa);
        }
        return toResponse(liste);
    }

    @Override
    @Transactional
    public HareketResponse masrafEkle(MasrafRequest r) {
        UUID tenantId = CurrentUser.tenantId();
        Hesap kaynak = paraHesabiGetir(r.kaynakHesapId());

        Arac arac = null;
        Hesap hedef;
        MasrafKok kok;
        HareketTur tur;
        if (r.aracId() != null) {
            arac = aracGetir(r.aracId());
            hedef = aracHesabi(arac);
            kok = MasrafKok.ARAC;
            tur = HareketTur.ARAC_MASRAF;
        } else {
            hedef = sistemHesabi(tenantId, HesapTur.GIDER);
            kok = MasrafKok.GENEL;
            tur = HareketTur.GENEL_GIDER;
        }
        MasrafKategori kategori = kategoriBulYaDaOlustur(tenantId, kok, r.kategoriId(), r.kategoriAd(), r.ustKategoriId());

        Hareket h = yeniHareket(tenantId, tur, kaynak, hedef, r.tutarKurus(), r.tarih());
        h.setArac(arac);
        h.setKategori(kategori);
        h.setAciklama(bosIseNull(r.aciklama()));
        return toResponse(hareketRepository.save(h), false);
    }

    @Override
    @Transactional
    public HareketResponse duzelt(UUID hareketId) {
        Hareket asil = hareketRepository.findById(hareketId).orElseThrow(KayitBulunamadiException::new);
        if (!asil.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        if (asil.getDuzeltilenHareketId() != null) throw new DuzeltmeDuzeltilemezException();
        if (hareketRepository.existsByDuzeltilenHareketId(asil.getId())) throw new HareketZatenDuzeltildiException();
        return toResponse(tersYaz(asil), false);
    }

    // ------------------------------------------------------------------ kategoriler

    @Override
    @Transactional(readOnly = true)
    public List<KategoriResponse> kategoriler(MasrafKok kok) {
        UUID tenantId = CurrentUser.tenantId();
        Map<UUID, Long> kullanim = new HashMap<>();
        for (Object[] satir : hareketRepository.kategoriKullanimlari(tenantId)) {
            kullanim.put((UUID) satir[0], ((Number) satir[1]).longValue());
        }
        return kategoriRepository.findByTenantIdAndKokAndArsivlendiFalse(tenantId, kok).stream()
                .map(k -> new KategoriResponse(k.getId(), k.getAd(), k.getKok(), k.getUstId(), kullanim.getOrDefault(k.getId(), 0L)))
                .sorted(Comparator.comparingLong(KategoriResponse::kullanim).reversed()
                        .thenComparing(KategoriResponse::ad, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ------------------------------------------------------------------ arac

    @Override
    @Transactional(readOnly = true)
    public AracMaliyet aracMaliyeti(UUID aracId) {
        Arac arac = aracGetir(aracId);
        List<Hareket> hareketler = hareketRepository.aracHareketleri(arac.getId());

        // Duzeltmeler ters yonde oldugu icin isaretli toplamda kendiliginden duser.
        long masraf = 0;
        for (Hareket h : hareketler) {
            if (h.getTur() != HareketTur.ARAC_MASRAF) continue;
            masraf += h.getHedefHesap().getTur() == HesapTur.ARAC ? h.getTutarKurus() : -h.getTutarKurus();
        }
        boolean alisDefterde = aktifAlis(arac.getId()) != null;

        // Alis her zaman aracin gercek alis bedelinden: alis deftere yazilmamis olsa da maliyet dogru kalir.
        long alis = arac.getGercekAlisKurus() == null ? 0 : arac.getGercekAlisKurus();
        long toplam = alis + masraf;
        Long satis = arac.getSatisKurus();
        Long kar = arac.getDurum() != AracDurum.STOKTA && satis != null ? satis - toplam : null;
        return new AracMaliyet(arac.getId(), alis, alisDefterde, masraf, toplam, satis, kar, toResponse(hareketler));
    }

    @Override
    @Transactional
    public void aracAlisiniEsitle(Arac arac, UUID alisHesapId) {
        long tutar = arac.getGercekAlisKurus() == null ? 0 : arac.getGercekAlisKurus();
        boolean yazilmali = alisHesapId != null && tutar > 0;
        Hareket aktif = aktifAlis(arac.getId());

        if (aktif != null && yazilmali
                && aktif.getKaynakHesap().getId().equals(alisHesapId)
                && aktif.getTutarKurus() == tutar
                && aktif.getTarih().equals(arac.getAlisTarihi())) {
            return;   // degisiklik yok
        }
        if (aktif == null && !yazilmali) return;

        Hesap kaynak = yazilmali ? paraHesabiGetir(alisHesapId) : null;
        if (aktif != null) tersYaz(aktif);
        if (yazilmali) {
            Hareket h = yeniHareket(arac.getTenantId(), HareketTur.ARAC_ALIS, kaynak, aracHesabi(arac), tutar, arac.getAlisTarihi());
            h.setArac(arac);
            h.setAciklama("Araç alışı");
            hareketRepository.save(h);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UUID alisHesapId(UUID aracId) {
        if (aracId == null) return null;
        Hareket aktif = aktifAlis(aracId);
        return aktif == null ? null : aktif.getKaynakHesap().getId();
    }

    // ------------------------------------------------------------------ yardimcilar

    /** Duzeltilmemis tek ARAC_ALIS hareketi (duzeltme hareketleri haric). */
    private Hareket aktifAlis(UUID aracId) {
        List<Hareket> alislar = hareketRepository.findByArac_IdAndTur(aracId, HareketTur.ARAC_ALIS).stream()
                .filter(h -> h.getDuzeltilenHareketId() == null)
                .toList();
        if (alislar.isEmpty()) return null;
        Set<UUID> duzeltilen = new HashSet<>(hareketRepository.duzeltilenler(alislar.stream().map(Hareket::getId).toList()));
        return alislar.stream().filter(h -> !duzeltilen.contains(h.getId())).findFirst().orElse(null);
    }

    private Hareket tersYaz(Hareket asil) {
        Hareket ters = yeniHareket(asil.getTenantId(), asil.getTur(), asil.getHedefHesap(), asil.getKaynakHesap(),
                asil.getTutarKurus(), asil.getTarih());   // ayni tarih: donem toplamlari sifirlanir
        ters.setArac(asil.getArac());
        ters.setKategori(asil.getKategori());
        ters.setAciklama("Düzeltme" + (asil.getAciklama() == null ? "" : ": " + asil.getAciklama()));
        ters.setDuzeltilenHareketId(asil.getId());
        return hareketRepository.save(ters);
    }

    private Hareket yeniHareket(UUID tenantId, HareketTur tur, Hesap kaynak, Hesap hedef, long tutar, LocalDate tarih) {
        Hareket h = new Hareket();
        h.setTenantId(tenantId);
        h.setTur(tur);
        h.setKaynakHesap(kaynak);
        h.setHedefHesap(hedef);
        h.setTutarKurus(tutar);
        h.setTarih(tarih);
        h.setOlusturan(panelUserRepository.getReferenceById(CurrentUser.principal().userId()));
        return h;
    }

    private Map<UUID, Long> bakiyeler(UUID tenantId) {
        Map<UUID, Long> sonuc = new HashMap<>();
        for (Object[] s : hareketRepository.girenToplamlari(tenantId)) sonuc.merge((UUID) s[0], ((Number) s[1]).longValue(), Long::sum);
        for (Object[] s : hareketRepository.cikanToplamlari(tenantId)) sonuc.merge((UUID) s[0], -((Number) s[1]).longValue(), Long::sum);
        return sonuc;
    }

    private Hesap sistemHesabi(UUID tenantId, HesapTur tur) {
        return hesapRepository.findFirstByTenantIdAndTur(tenantId, tur).orElseGet(() -> {
            Hesap h = new Hesap();
            h.setTenantId(tenantId);
            h.setTur(tur);
            h.setAd(tur == HesapTur.KASA ? "Kasa" : tur == HesapTur.GIDER ? "Genel giderler" : "Gelirler");
            return hesapRepository.save(h);
        });
    }

    private Hesap aracHesabi(Arac arac) {
        return hesapRepository.findByAracId(arac.getId()).orElseGet(() -> {
            Hesap h = new Hesap();
            h.setTenantId(arac.getTenantId());
            h.setTur(HesapTur.ARAC);
            h.setAracId(arac.getId());
            h.setAd("Araç " + arac.getPlaka());
            return hesapRepository.save(h);
        });
    }

    private Hesap hesapGetir(UUID id) {
        Hesap h = hesapRepository.findById(id).orElseThrow(KayitBulunamadiException::new);
        if (!h.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        return h;
    }

    private Hesap paraHesabiGetir(UUID id) {
        Hesap h = hesapGetir(id);
        if (!h.getTur().paraHesabi()) throw new GecersizHesapException();
        return h;
    }

    private Arac aracGetir(UUID id) {
        Arac arac = aracRepository.findById(id).orElseThrow(KayitBulunamadiException::new);
        if (!arac.getTenantId().equals(CurrentUser.tenantId())) throw new SourceNotBelongToTenantException();
        return arac;
    }

    private MasrafKategori kategoriBulYaDaOlustur(UUID tenantId, MasrafKok kok, UUID kategoriId, String ad, UUID ustId) {
        if (kategoriId != null) return kategoriGetir(kategoriId, tenantId, kok);

        String temiz = bosIseNull(ad);
        if (temiz == null) throw new GecersizKategoriException();
        MasrafKategori ust = ustId == null ? null : kategoriGetir(ustId, tenantId, kok);
        if (ust != null && seviye(ust) >= KATEGORI_EN_FAZLA_SEVIYE) throw new KategoriSeviyeSiniriException();

        return (ust == null
                ? kategoriRepository.findFirstByTenantIdAndKokAndUstIdIsNullAndAdIgnoreCase(tenantId, kok, temiz)
                : kategoriRepository.findFirstByTenantIdAndKokAndUstIdAndAdIgnoreCase(tenantId, kok, ust.getId(), temiz))
                .orElseGet(() -> {
                    MasrafKategori k = new MasrafKategori();
                    k.setTenantId(tenantId);
                    k.setKok(kok);
                    k.setUstId(ust == null ? null : ust.getId());
                    k.setAd(ilkHarfBuyuk(temiz));
                    return kategoriRepository.save(k);
                });
    }

    private MasrafKategori kategoriGetir(UUID id, UUID tenantId, MasrafKok kok) {
        MasrafKategori k = kategoriRepository.findById(id).orElseThrow(KayitBulunamadiException::new);
        if (!k.getTenantId().equals(tenantId)) throw new SourceNotBelongToTenantException();
        if (k.getKok() != kok) throw new GecersizKategoriException();
        return k;
    }

    /** Kok seviyedeki kategori 1. */
    private int seviye(MasrafKategori k) {
        int seviye = 1;
        UUID ustId = k.getUstId();
        while (ustId != null && seviye <= KATEGORI_EN_FAZLA_SEVIYE) {
            seviye++;
            ustId = kategoriRepository.findById(ustId).map(MasrafKategori::getUstId).orElse(null);
        }
        return seviye;
    }

    private List<HareketResponse> toResponse(List<Hareket> liste) {
        if (liste.isEmpty()) return List.of();
        Set<UUID> duzeltilen = new HashSet<>(hareketRepository.duzeltilenler(liste.stream().map(Hareket::getId).toList()));
        return liste.stream().map(h -> toResponse(h, duzeltilen.contains(h.getId()))).toList();
    }

    private static HareketResponse toResponse(Hareket h, boolean duzeltildi) {
        Arac a = h.getArac();
        return new HareketResponse(h.getId(), h.getTarih(), h.getTur(),
                hesapOzet(h.getKaynakHesap()), hesapOzet(h.getHedefHesap()), h.getTutarKurus(),
                a == null ? null : new AracKisa(a.getId(), a.getPlaka(), a.getMarka(), a.getModel()),
                h.getKategori() == null ? null : h.getKategori().getAd(),
                h.getAciklama(), h.getDuzeltilenHareketId(), duzeltildi,
                h.getOlusturan() == null ? null : h.getOlusturan().getFullName(),
                h.getCreatedAt());
    }

    private static HesapOzet hesapOzet(Hesap h) {
        return new HesapOzet(h.getId(), h.getTur(), h.getAd());
    }

    /** "kaporta" -> "Kaporta", "ışık" -> "Işık" (Turkce buyuk harf). */
    static String ilkHarfBuyuk(String s) {
        return s.substring(0, 1).toUpperCase(TURKCE) + s.substring(1);
    }

    private static String bosIseNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
