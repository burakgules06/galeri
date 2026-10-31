package com.galeri.app.controller;

import com.galeri.app.model.request.CapitalRequests;
import com.galeri.app.service.CapitalService;
import com.galeri.app.service.LedgerService;
import com.galeri.app.service.impl.GenericResponseService;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Sermaye, donem kari, virman ve acilis bakiyesi sadece ADMIN: @PreAuthorize calisiyor mu (veritabanisiz kucuk baglam).
@SpringJUnitConfig(CapitalSecurityTest.Config.class)
class CapitalSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean CapitalService capitalService() { return Mockito.mock(CapitalService.class); }
        @Bean LedgerService ledgerService() { return Mockito.mock(LedgerService.class); }
        @Bean com.galeri.app.service.NetWorthService netWorthService() { return Mockito.mock(com.galeri.app.service.NetWorthService.class); }
        @Bean NetWorthController netWorthController(com.galeri.app.service.NetWorthService s, GenericResponseService r) { return new NetWorthController(s, r); }
        @Bean GenericResponseService genericResponseService() { return new GenericResponseService(); }
        @Bean CapitalController capitalController(CapitalService c, LedgerService l, GenericResponseService r) { return new CapitalController(c, l, r); }
    }

    @Autowired private CapitalController capital;
    @Autowired private NetWorthController netWorth;

    private static final LocalDate DAY = LocalDate.of(2026, 7, 1);

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void calisan_sermayeVeKasaIslemlerineErisemez_403() {
        TestSession.open(UUID.randomUUID(), UUID.randomUUID(), "STAFF");
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> netWorth.summary()).isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> capital.partners()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> capital.removePartner(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> capital.move(id, new CapitalRequests.Movement(CapitalRequests.Direction.IN, id, DAY, 1L, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> capital.periodProfit(DAY, DAY)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> capital.allocate(new CapitalRequests.Allocation(id, DAY, 1L, DAY, DAY))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> capital.transfer(new CapitalRequests.Transfer(id, id, DAY, 1L, null))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> capital.openingBalance(new CapitalRequests.Opening(id, null, null, DAY, 1L))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void yonetici_donemKarinaErisir() {
        TestSession.open(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");

        assertThatCode(() -> capital.periodProfit(DAY, DAY)).doesNotThrowAnyException();
        assertThatCode(() -> netWorth.summary()).doesNotThrowAnyException();
    }
}
