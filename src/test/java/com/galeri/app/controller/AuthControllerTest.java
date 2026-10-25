package com.galeri.app.controller;

import com.galeri.app.constant.ErrorMessageConstant;
import com.galeri.app.model.GenericResponse;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Tenant;
import com.galeri.app.model.response.LoginResponse;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.security.JwtService;
import com.galeri.app.service.UserService;
import com.galeri.app.service.impl.GenericResponseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private PanelUserRepository panelUserRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private UserService userService;

    private AuthController controller;
    private PanelUser user;

    @BeforeEach
    void setUp() {
        controller = new AuthController(panelUserRepository, passwordEncoder, jwtService, new GenericResponseService(), userService);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setDisplayName("Demo Galeri");

        user = new PanelUser();
        user.setId(UUID.randomUUID());
        user.setTenant(tenant);
        user.setPhone("905550000000");
        user.setPasswordHash("hash");
        user.setFullName("Demo Yonetici");
        user.setRole("ADMIN");
    }

    @Test
    void dogruSifre_tokenDoner_telefonNormalizeEdilir() {
        when(panelUserRepository.findByPhoneAndActiveTrue("905550000000")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("demo1234", "hash")).thenReturn(true);
        when(jwtService.generateToken(any(), any(), anyString())).thenReturn("jwt-token");

        ResponseEntity<GenericResponse<LoginResponse>> res =
                controller.login(new AuthController.LoginRequest("0555 000 00 00", "demo1234"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().getData().token()).isEqualTo("jwt-token");
        assertThat(res.getBody().getData().tenantName()).isEqualTo("Demo Galeri");
    }

    @Test
    void yanlisSifre_401Doner() {
        when(panelUserRepository.findByPhoneAndActiveTrue("905550000000")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("yanlis", "hash")).thenReturn(false);

        ResponseEntity<GenericResponse<LoginResponse>> res =
                controller.login(new AuthController.LoginRequest("5550000000", "yanlis"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody().getCode()).isEqualTo(ErrorMessageConstant.INVALID_CREDENTIALS);
    }

    @Test
    void pasifKullanici_dogruSifreyleDeGirisYapamaz_401() {
        user.setActive(false);
        // Sorgu sadece aktif kullanicilari doner
        when(panelUserRepository.findByPhoneAndActiveTrue("905550000000")).thenReturn(Optional.empty());

        ResponseEntity<GenericResponse<LoginResponse>> res =
                controller.login(new AuthController.LoginRequest("5550000000", "demo1234"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void bilinmeyenKullanici_401Doner() {
        when(panelUserRepository.findByPhoneAndActiveTrue(anyString())).thenReturn(Optional.empty());

        ResponseEntity<GenericResponse<LoginResponse>> res =
                controller.login(new AuthController.LoginRequest("5559999999", "demo1234"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
