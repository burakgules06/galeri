package com.galeri.app.service.impl;

import com.galeri.app.model.entity.Arac;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.response.AylikAlimSatim;
import com.galeri.app.model.response.AylikAlimSatim.AylikArac;
import com.galeri.app.model.response.AylikAlimSatim.CalisanAylik;
import com.galeri.app.repository.AracRepository;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.security.CurrentUser;
import com.galeri.app.service.RaporService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class RaporServiceImpl implements RaporService {

    static final String BELIRTILMEMIS = "Belirtilmemiş";

    private final AracRepository aracRepository;
    private final PanelUserRepository panelUserRepository;

    @Override
    @Transactional(readOnly = true)
    public AylikAlimSatim aylikAlimSatim(int yil, int ay) {
        UUID tenantId = CurrentUser.tenantId();
        YearMonth donem = YearMonth.of(yil, ay);
        LocalDate bas = donem.atDay(1);
        LocalDate son = donem.atEndOfMonth();

        List<Arac> alinanlar = aracRepository.findByTenantIdAndAlisTarihiBetween(tenantId, bas, son);
        List<Arac> satilanlar = aracRepository.findByTenantIdAndSatisTarihiBetween(tenantId, bas, son);

        // Satirlar: ADMIN icin tum aktif calisanlar (0 olanlar dahil), STAFF icin sadece kendisi.
        Map<UUID, Satir> satirlar = new LinkedHashMap<>();
        if (CurrentUser.isAdmin()) {
            for (PanelUser u : panelUserRepository.findByTenant_IdOrderByFullName(tenantId)) {
                if (u.isActive()) satirlar.put(u.getId(), new Satir(u.getId(), u.getFullName()));
            }
        } else {
            UUID ben = CurrentUser.principal().userId();
            panelUserRepository.findById(ben).ifPresent(u -> satirlar.put(u.getId(), new Satir(u.getId(), u.getFullName())));
        }

        dagit(alinanlar, Arac::getAlanCalisan, Arac::getAlisTarihi, satirlar, true);
        dagit(satilanlar, Arac::getSatanCalisan, Arac::getSatisTarihi, satirlar, false);

        List<CalisanAylik> sonuc = satirlar.values().stream()
                .map(Satir::toResponse)
                .toList();
        return new AylikAlimSatim(yil, ay, sonuc);
    }

    /**
     * Araclari calisan satirlarina dagitir. Calisani girilmemis araclar sadece ADMIN'de
     * "Belirtilmemis" satirina duser; STAFF'ta satiri olmayan calisanin araclari atlanir.
     */
    private static void dagit(List<Arac> araclar, Function<Arac, PanelUser> calisan, Function<Arac, LocalDate> tarih,
                              Map<UUID, Satir> satirlar, boolean alis) {
        boolean admin = CurrentUser.isAdmin();
        for (Arac a : araclar) {
            PanelUser u = calisan.apply(a);
            UUID calisanId = u == null ? null : u.getId();
            Satir satir = satirlar.get(calisanId);
            if (satir == null) {
                if (!admin) continue;
                satir = new Satir(calisanId, u == null ? BELIRTILMEMIS : u.getFullName());
                satirlar.put(calisanId, satir);
            }
            AylikArac kayit = new AylikArac(a.getId(), a.getPlaka(), a.getMarka(), a.getModel(), tarih.apply(a));
            (alis ? satir.alinanlar : satir.satilanlar).add(kayit);
        }
    }

    private static final class Satir {
        final UUID calisanId;
        final String ad;
        final List<AylikArac> alinanlar = new ArrayList<>();
        final List<AylikArac> satilanlar = new ArrayList<>();

        Satir(UUID calisanId, String ad) {
            this.calisanId = calisanId;
            this.ad = Objects.requireNonNullElse(ad, BELIRTILMEMIS);
        }

        CalisanAylik toResponse() {
            Comparator<AylikArac> tarihe = Comparator.comparing(AylikArac::tarih);
            alinanlar.sort(tarihe);
            satilanlar.sort(tarihe);
            return new CalisanAylik(calisanId, ad, List.copyOf(alinanlar), List.copyOf(satilanlar));
        }
    }
}
