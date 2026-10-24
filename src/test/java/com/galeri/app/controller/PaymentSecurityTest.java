package com.galeri.app.controller;

import com.galeri.app.service.PaymentPlanService;
import com.galeri.app.service.VehicleService;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @PreAuthorize gercekten calisiyor mu: veritabanisiz kucuk bir Spring baglami, method security acik.
// AccessDeniedException'i GlobalExceptionHandler 403'e cevirir.
@SpringJUnitConfig(PaymentSecurityTest.Config.class)
class PaymentSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean PaymentPlanService paymentPlanService() { return Mockito.mock(PaymentPlanService.class); }
        @Bean VehicleService vehicleService() { return Mockito.mock(VehicleService.class); }
        @Bean GenericResponseService genericResponseService() { return new GenericResponseService(); }
        @Bean InstallmentController installmentController(PaymentPlanService s, GenericResponseService r) { return new InstallmentController(s, r); }
        @Bean VehicleController vehicleController(VehicleService v, PaymentPlanService s, GenericResponseService r) { return new VehicleController(v, s, r); }
    }

    @Autowired private InstallmentController installments;
    @Autowired private VehicleController vehicles;

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void calisan_taksitlereVeOdemePlaninaErisemez_403() {
        TestSession.open(UUID.randomUUID(), UUID.randomUUID(), "STAFF");

        assertThatThrownBy(() -> installments.list(null, null, null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> installments.undo(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> vehicles.paymentPlans(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> vehicles.undoSale(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void yonetici_taksitListesineErisir() {
        TestSession.open(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");

        assertThatCode(() -> installments.list(null, null, null, null)).doesNotThrowAnyException();
    }
}
