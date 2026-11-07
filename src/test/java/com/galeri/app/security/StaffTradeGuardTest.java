package com.galeri.app.security;

import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Tenant;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffTradeGuardTest {

    private final PanelUserRepository repo = mock(PanelUserRepository.class);
    private final StaffTradeGuard guard = new StaffTradeGuard(repo);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    private void user(boolean active, boolean canTrade, UUID tenant) {
        user(active, canTrade, false, tenant);
    }

    private void user(boolean active, boolean canTrade, boolean canAddExpense, UUID tenant) {
        Tenant t = new Tenant();
        t.setId(tenant);
        PanelUser u = new PanelUser();
        u.setId(userId);
        u.setTenant(t);
        u.setRole("STAFF");
        u.setActive(active);
        u.setCanTrade(canTrade);
        u.setCanAddExpense(canAddExpense);
        when(repo.findById(userId)).thenReturn(Optional.of(u));
    }

    @Test
    void yetkiliAktifCalisan_izinAlir() {
        TestSession.open(userId, tenantId, "STAFF");
        user(true, true, tenantId);
        assertThat(guard.allowed()).isTrue();
    }

    @Test
    void yetkisiKaldirilanVeyaPasifCalisan_reddedilir() {
        TestSession.open(userId, tenantId, "STAFF");
        user(true, false, tenantId);
        assertThat(guard.allowed()).as("yetki kaldirildi").isFalse();
        user(false, true, tenantId);
        assertThat(guard.allowed()).as("pasif").isFalse();
        user(true, true, UUID.randomUUID());
        assertThat(guard.allowed()).as("baska galeri").isFalse();
    }

    @Test
    void calisanOlmayanRol_buKapidanGecmez() {
        TestSession.open(userId, tenantId, "INVESTOR");
        user(true, true, tenantId);
        assertThat(guard.allowed()).isFalse();
    }

    @Test
    void oturumYoksa_reddedilir() {
        assertThat(guard.allowed()).isFalse();
    }

    @Test
    void masrafYetkisi_alimSatimYetkisindenBagimsizdir() {
        TestSession.open(userId, tenantId, "STAFF");
        user(true, false, true, tenantId);
        assertThat(guard.expenseAllowed()).isTrue();
        assertThat(guard.allowed()).isFalse();
        user(true, true, false, tenantId);
        assertThat(guard.expenseAllowed()).isFalse();
        user(false, false, true, tenantId);
        assertThat(guard.expenseAllowed()).as("pasif").isFalse();
    }
}
