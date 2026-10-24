package com.galeri.app.service.impl;

import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Vehicle;
import com.galeri.app.model.response.MonthlyTrades;
import com.galeri.app.model.response.MonthlyTrades.StaffMonthly;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.VehicleRepository;
import com.galeri.app.support.TestSession;
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
class ReportServiceImplTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private PanelUserRepository panelUserRepository;

    private final UUID tenantId = UUID.randomUUID();
    private final LocalDate start = LocalDate.of(2026, 6, 1);
    private final LocalDate end = LocalDate.of(2026, 6, 30);

    private PanelUser ahmet;
    private PanelUser veli;
    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(vehicleRepository, panelUserRepository);
        ahmet = user("Ahmet");
        veli = user("Veli");
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void yonetici_tumCalisanlariVeBelirtilmemisleriGorur() {
        TestSession.open(UUID.randomUUID(), tenantId, "ADMIN");
        when(panelUserRepository.findByTenant_IdOrderByFullName(tenantId)).thenReturn(List.of(ahmet, veli));
        when(vehicleRepository.findByTenantIdAndPurchaseDateBetween(tenantId, start, end)).thenReturn(List.of(
                vehicle("06 ABC 123", ahmet, LocalDate.of(2026, 6, 10), null, null),
                vehicle("34 KLM 45", null, LocalDate.of(2026, 6, 3), null, null)));
        when(vehicleRepository.findByTenantIdAndSaleDateBetween(tenantId, start, end)).thenReturn(List.of(
                vehicle("06 TR 7788", null, LocalDate.of(2026, 5, 1), ahmet, LocalDate.of(2026, 6, 28))));

        MonthlyTrades result = service.monthlyTrades(2026, 6);

        assertThat(result.staff()).extracting(StaffMonthly::name).containsExactly("Ahmet", "Veli", "Belirtilmemiş");
        StaffMonthly ahmetRow = result.staff().get(0);
        assertThat(ahmetRow.bought()).extracting(v -> v.plate()).containsExactly("06 ABC 123");
        assertThat(ahmetRow.sold()).extracting(v -> v.plate()).containsExactly("06 TR 7788");
        assertThat(result.staff().get(1).bought()).isEmpty();
        assertThat(result.staff().get(2).bought()).extracting(v -> v.plate()).containsExactly("34 KLM 45");
    }

    @Test
    void calisan_sadeceKendiAldigiVeSattigiAraclariGorur() {
        TestSession.open(ahmet.getId(), tenantId, "STAFF");
        when(panelUserRepository.findById(ahmet.getId())).thenReturn(Optional.of(ahmet));
        when(vehicleRepository.findByTenantIdAndPurchaseDateBetween(tenantId, start, end)).thenReturn(List.of(
                vehicle("06 ABC 123", ahmet, LocalDate.of(2026, 6, 10), null, null),
                vehicle("35 VL 100", veli, LocalDate.of(2026, 6, 11), null, null),
                vehicle("34 KLM 45", null, LocalDate.of(2026, 6, 3), null, null)));
        when(vehicleRepository.findByTenantIdAndSaleDateBetween(tenantId, start, end)).thenReturn(List.of());

        MonthlyTrades result = service.monthlyTrades(2026, 6);

        assertThat(result.staff()).hasSize(1);
        assertThat(result.staff().get(0).name()).isEqualTo("Ahmet");
        assertThat(result.staff().get(0).bought()).extracting(v -> v.plate()).containsExactly("06 ABC 123");
    }

    private static PanelUser user(String name) {
        PanelUser u = new PanelUser();
        u.setId(UUID.randomUUID());
        u.setFullName(name);
        u.setActive(true);
        return u;
    }

    private static Vehicle vehicle(String plate, PanelUser boughtBy, LocalDate purchaseDate, PanelUser soldBy, LocalDate saleDate) {
        Vehicle v = new Vehicle();
        v.setId(UUID.randomUUID());
        v.setPlate(plate);
        v.setMake("Marka");
        v.setModel("Model");
        v.setBoughtBy(boughtBy);
        v.setPurchaseDate(purchaseDate);
        v.setSoldBy(soldBy);
        v.setSaleDate(saleDate);
        return v;
    }
}
