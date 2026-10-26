package com.galeri.app.service.impl;

import com.galeri.app.model.entity.CatalogEntry;
import com.galeri.app.repository.CatalogEntryRepository;
import com.galeri.app.service.CatalogService;
import com.galeri.app.service.CatalogService.Resolved;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Gercek katalog dosyasiyla (resources/catalog/vehicles.json).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogServiceImplTest {

    @Mock private CatalogEntryRepository entryRepository;

    private final UUID tenantId = UUID.randomUUID();
    private CatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CatalogServiceImpl(entryRepository);
        TestSession.open(UUID.randomUUID(), tenantId, "ADMIN");
        when(entryRepository.findByTenantId(tenantId)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void katalog_enAz40Marka_tekrarsiz_herMarkadaModelVar() {
        Map<String, List<String>> base = CatalogServiceImpl.load(CatalogServiceImpl.RESOURCE);

        assertThat(base).hasSizeGreaterThanOrEqualTo(40);
        assertThat(base.keySet().stream().map(CatalogServiceImpl::key).distinct().count()).isEqualTo(base.size());
        base.forEach((make, models) -> {
            assertThat(models).as(make).isNotEmpty();
            assertThat(models.stream().map(CatalogServiceImpl::key).distinct().count()).as(make).isEqualTo(models.size());
        });
        assertThat(service.makes()).extracting(CatalogService.Item::name).contains("Fiat", "Renault", "Togg", "Tofaş", "Volkswagen");
    }

    @Test
    void cozumleme_harfBuyuklugundenBagimsiz_paketKorunur_enUzunModelSecilir() {
        assertThat(service.resolve(tenantId, "volkswagen", "PASSAT 1.5 TSI Business"))
                .contains(new Resolved("Volkswagen", "Passat 1.5 TSI Business"));
        assertThat(service.resolve(tenantId, "Fiat", "Egea Cross 1.4 Fire")).contains(new Resolved("Fiat", "Egea Cross 1.4 Fire"));
        assertThat(service.resolve(tenantId, "Fiat", "Egea")).contains(new Resolved("Fiat", "Egea"));
        assertThat(service.resolve(tenantId, "TOFAŞ", "ŞAHİN S")).contains(new Resolved("Tofaş", "Şahin S"));   // Turkce I/İ
        assertThat(service.resolve(tenantId, "BMW", "3 Serisi 320i M Sport")).contains(new Resolved("BMW", "3 Serisi 320i M Sport"));
    }

    @Test
    void katalogdaOlmayan_cozulmez_modelOnEkiTekBasinaYetmez() {
        assertThat(service.resolve(tenantId, "Fiat", "Egeaa")).isEmpty();         // "Egea" + bosluk degil
        assertThat(service.resolve(tenantId, "Yugo", "45")).isEmpty();
        assertThat(service.resolve(tenantId, "Fiat", " ")).isEqualTo(Optional.empty());
    }

    @Test
    void galeriEklemesi_cozulur_vanaKatalogdaVarsaEklenmez() {
        CatalogEntry yugo = new CatalogEntry();
        yugo.setTenantId(tenantId);
        yugo.setMake("Yugo");
        yugo.setModel("45");
        when(entryRepository.findByTenantId(tenantId)).thenReturn(List.of(yugo));

        assertThat(service.resolve(tenantId, "yugo", "45 GL")).contains(new Resolved("Yugo", "45 GL"));
        assertThat(service.makes()).anySatisfy(i -> assertThat(i).isEqualTo(new CatalogService.Item("Yugo", true)));
        assertThat(service.models("Yugo")).containsExactly(new CatalogService.Item("45", true));

        service.add(tenantId, "fiat", "egea");     // ana katalogda var
        service.add(tenantId, "Yugo", "45");       // zaten eklenmis
        verify(entryRepository, never()).save(any());

        service.add(tenantId, "fiat", "Uno");      // yeni: markanin katalog yazimiyla eklenir
        verify(entryRepository).save(org.mockito.ArgumentMatchers.argThat(e -> e.getMake().equals("Fiat") && e.getModel().equals("Uno")));
    }
}
