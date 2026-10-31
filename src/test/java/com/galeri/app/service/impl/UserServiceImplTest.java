package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.InvalidPhoneException;
import com.galeri.app.exception.runtime.LastActiveAdminException;
import com.galeri.app.exception.runtime.PhoneAlreadyUsedException;
import com.galeri.app.exception.runtime.SelfChangeNotAllowedException;
import com.galeri.app.exception.runtime.SourceNotBelongToTenantException;
import com.galeri.app.exception.runtime.WrongPasswordException;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Tenant;
import com.galeri.app.model.request.UserRequests;
import com.galeri.app.model.response.UserResponse;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.TenantRepository;
import com.galeri.app.support.TestSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock private PanelUserRepository panelUserRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private com.galeri.app.repository.AccountRepository accountRepository;
    @Mock private com.galeri.app.repository.VehicleShareRepository shareRepository;
    @Mock private com.galeri.app.repository.VehicleRepository vehicleRepository;

    private UserServiceImpl service;
    private Tenant tenant;
    private PanelUser me;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(panelUserRepository, tenantRepository, passwordEncoder, accountRepository, shareRepository, vehicleRepository);
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        me = user(tenant, "ADMIN", true);
        TestSession.open(me.getId(), tenant.getId(), "ADMIN");
        when(passwordEncoder.encode(anyString())).thenAnswer(a -> "hash:" + a.getArgument(0));
        when(tenantRepository.getReferenceById(tenant.getId())).thenReturn(tenant);
    }

    @AfterEach
    void tearDown() {
        TestSession.close();
    }

    // ------------------------------------------------------------------ yonetici kurallari

    @Test
    void sonAktifYonetici_pasiflestirilemezVeRoluDusurulemez() {
        when(panelUserRepository.countByTenant_IdAndRoleAndActiveTrue(tenant.getId(), "ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.update(me.getId(), new UserRequests.Update("Ben", "ADMIN", false)))
                .isInstanceOf(LastActiveAdminException.class);
        assertThatThrownBy(() -> service.update(me.getId(), new UserRequests.Update("Ben", "STAFF", true)))
                .isInstanceOf(LastActiveAdminException.class);
        assertThat(me.isActive()).isTrue();
        assertThat(me.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void yoneticiKendini_baskaYoneticiVarkenDePasiflestiremez() {
        when(panelUserRepository.countByTenant_IdAndRoleAndActiveTrue(tenant.getId(), "ADMIN")).thenReturn(2L);

        assertThatThrownBy(() -> service.update(me.getId(), new UserRequests.Update("Ben", "ADMIN", false)))
                .isInstanceOf(SelfChangeNotAllowedException.class);
        assertThatThrownBy(() -> service.update(me.getId(), new UserRequests.Update("Ben", "STAFF", true)))
                .isInstanceOf(SelfChangeNotAllowedException.class);
    }

    @Test
    void baskaYoneticiyi_digerYoneticiVarkenPasiflestirebilir() {
        PanelUser other = user(tenant, "ADMIN", true);
        when(panelUserRepository.countByTenant_IdAndRoleAndActiveTrue(tenant.getId(), "ADMIN")).thenReturn(2L);

        UserResponse res = service.update(other.getId(), new UserRequests.Update("  Selin Arslan ", "STAFF", false));

        assertThat(other.isActive()).isFalse();
        assertThat(other.getRole()).isEqualTo("STAFF");
        assertThat(res.fullName()).isEqualTo("Selin Arslan");
        assertThat(res.self()).isFalse();
    }

    @Test
    void calisaniPasiflestirmek_yoneticiSayisinaBakmaz() {
        PanelUser staff = user(tenant, "STAFF", true);

        assertThatCode(() -> service.update(staff.getId(), new UserRequests.Update("Mert", "STAFF", false))).doesNotThrowAnyException();
        assertThat(staff.isActive()).isFalse();
        verify(panelUserRepository, never()).countByTenant_IdAndRoleAndActiveTrue(any(), anyString());
    }

    // ------------------------------------------------------------------ cok kiracilik

    @Test
    void baskaGalerininKullanicisi_bulunamaz() {
        Tenant other = new Tenant();
        other.setId(UUID.randomUUID());
        PanelUser foreign = user(other, "STAFF", true);

        assertThatThrownBy(() -> service.update(foreign.getId(), new UserRequests.Update("X", "STAFF", false)))
                .isInstanceOf(SourceNotBelongToTenantException.class);
        assertThatThrownBy(() -> service.resetPassword(foreign.getId(), new UserRequests.ResetPassword("yeni-sifre-1")))
                .isInstanceOf(SourceNotBelongToTenantException.class);
        assertThat(foreign.isActive()).isTrue();
        assertThat(foreign.getPasswordHash()).isEqualTo("eski");
    }

    // ------------------------------------------------------------------ ekleme

    @Test
    void ekleme_telefonuNormalizeEder_galeriyeVeRoleBaglar() {
        when(panelUserRepository.existsByPhone("905301112233")).thenReturn(false);

        UserResponse res = service.create(new UserRequests.Create(" Mert Kaya ", "0530 111 22 33", "STAFF", "baslangic-1"));

        ArgumentCaptor<PanelUser> saved = ArgumentCaptor.forClass(PanelUser.class);
        verify(panelUserRepository).save(saved.capture());
        assertThat(saved.getValue().getPhone()).isEqualTo("905301112233");
        assertThat(saved.getValue().getTenant()).isSameAs(tenant);
        assertThat(saved.getValue().getRole()).isEqualTo("STAFF");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash:baslangic-1");
        assertThat(res.fullName()).isEqualTo("Mert Kaya");
    }

    @Test
    void ayniTelefon_ikinciKezEklenemez() {
        when(panelUserRepository.existsByPhone("905301112233")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new UserRequests.Create("Mert", "+90 530 111 22 33", "STAFF", "baslangic-1")))
                .isInstanceOf(PhoneAlreadyUsedException.class);
        verify(panelUserRepository, never()).save(any());
    }

    @Test
    void gecersizTelefon_reddedilir() {
        assertThatThrownBy(() -> service.create(new UserRequests.Create("Mert", "212 555 00 00", "STAFF", "baslangic-1")))
                .isInstanceOf(InvalidPhoneException.class);
    }

    // ------------------------------------------------------------------ sifre

    @Test
    void sifreDegistirme_mevcutSifreYanlissa_reddedilir() {
        when(passwordEncoder.matches("yanlis", "eski")).thenReturn(false);

        assertThatThrownBy(() -> service.changeOwnPassword(new UserRequests.ChangePassword("yanlis", "yeni-sifre-1")))
                .isInstanceOf(WrongPasswordException.class);
        assertThat(me.getPasswordHash()).isEqualTo("eski");
    }

    @Test
    void sifreDegistirme_dogruSifreyle_yeniHashYazilir() {
        when(passwordEncoder.matches("dogru", "eski")).thenReturn(true);

        service.changeOwnPassword(new UserRequests.ChangePassword("dogru", "yeni-sifre-1"));

        assertThat(me.getPasswordHash()).isEqualTo("hash:yeni-sifre-1");
    }

    @Test
    void sifreSifirlama_ayniGalerideYeniHashYazar() {
        PanelUser staff = user(tenant, "STAFF", true);

        service.resetPassword(staff.getId(), new UserRequests.ResetPassword("gecici-sifre"));

        assertThat(staff.getPasswordHash()).isEqualTo("hash:gecici-sifre");
    }

    private PanelUser user(Tenant t, String role, boolean active) {
        PanelUser u = new PanelUser();
        u.setId(UUID.randomUUID());
        u.setTenant(t);
        u.setRole(role);
        u.setActive(active);
        u.setFullName("Kullanici");
        u.setPhone("90530" + (int) (Math.random() * 10_000_000));
        u.setPasswordHash("eski");
        when(panelUserRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }
}
