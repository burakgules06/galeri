package com.galeri.app.controller;

import com.galeri.app.model.request.UserRequests;
import com.galeri.app.service.UserService;
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

// Kullanici yonetimi sadece ADMIN: @PreAuthorize gercekten calisiyor mu (veritabanisiz kucuk baglam).
@SpringJUnitConfig(UserSecurityTest.Config.class)
class UserSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean UserService userService() { return Mockito.mock(UserService.class); }
        @Bean GenericResponseService genericResponseService() { return new GenericResponseService(); }
        @Bean com.galeri.app.service.LedgerService ledgerService() { return Mockito.mock(com.galeri.app.service.LedgerService.class); }
        @Bean UserController userController(UserService s, com.galeri.app.service.LedgerService l, GenericResponseService r) { return new UserController(s, l, r); }
    }

    @Autowired private UserController users;

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    @Test
    void calisan_kullaniciYonetimineErisemez_403() {
        TestSession.open(UUID.randomUUID(), UUID.randomUUID(), "STAFF");
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> users.list()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> users.create(new UserRequests.Create("A", "05301112233", "ADMIN", "sifre-1234")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> users.update(id, new UserRequests.Update("A", "ADMIN", true))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> users.resetPassword(id, new UserRequests.ResetPassword("sifre-1234"))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void yonetici_kullaniciListesineErisir() {
        TestSession.open(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");

        assertThatCode(() -> users.list()).doesNotThrowAnyException();
    }
}
