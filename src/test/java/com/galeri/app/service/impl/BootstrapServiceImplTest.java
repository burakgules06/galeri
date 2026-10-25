package com.galeri.app.service.impl;

import com.galeri.app.config.BootstrapProperties;
import com.galeri.app.model.entity.PanelUser;
import com.galeri.app.model.entity.Tenant;
import com.galeri.app.repository.PanelUserRepository;
import com.galeri.app.repository.TenantRepository;
import com.galeri.app.service.BootstrapService.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BootstrapServiceImplTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private PanelUserRepository panelUserRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private static final BootstrapProperties FULL =
            new BootstrapProperties("Yılmaz Otomotiv", "yilmaz-otomotiv", "Ali Yılmaz", "0530 111 22 33", "ilk-sifre-123");

    private BootstrapServiceImpl service(BootstrapProperties props) {
        when(passwordEncoder.encode("ilk-sifre-123")).thenReturn("bcrypt-hash");
        return new BootstrapServiceImpl(props, tenantRepository, panelUserRepository, passwordEncoder);
    }

    @Test
    void hicGaleriYokken_galeriVeYoneticiOlusturur() {
        when(tenantRepository.count()).thenReturn(0L);

        assertThat(service(FULL).run()).isEqualTo(Result.CREATED);

        ArgumentCaptor<Tenant> tenant = ArgumentCaptor.forClass(Tenant.class);
        ArgumentCaptor<PanelUser> admin = ArgumentCaptor.forClass(PanelUser.class);
        verify(tenantRepository).save(tenant.capture());
        verify(panelUserRepository).save(admin.capture());
        assertThat(tenant.getValue().getSlug()).isEqualTo("yilmaz-otomotiv");
        assertThat(tenant.getValue().getDisplayName()).isEqualTo("Yılmaz Otomotiv");
        assertThat(admin.getValue().getTenant()).isSameAs(tenant.getValue());
        assertThat(admin.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(admin.getValue().getPhone()).isEqualTo("905301112233");
        assertThat(admin.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
    }

    @Test
    void galeriVarsa_hicbirSeyeDokunmaz() {
        when(tenantRepository.count()).thenReturn(1L);

        assertThat(service(FULL).run()).isEqualTo(Result.SKIPPED);

        verify(tenantRepository, never()).save(any());
        verify(panelUserRepository, never()).save(any());
    }

    @Test
    void eksikDegiskende_calismaz() {
        BootstrapProperties missing = new BootstrapProperties("Yılmaz Otomotiv", "yilmaz-otomotiv", "Ali Yılmaz", "0530 111 22 33", " ");

        assertThat(service(missing).run()).isEqualTo(Result.INCOMPLETE);

        verify(tenantRepository, never()).count();
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void hicDegiskenYoksa_sessizceGecer() {
        assertThat(service(new BootstrapProperties("", "", "", "", "")).run()).isEqualTo(Result.NOT_CONFIGURED);

        verify(tenantRepository, never()).count();
    }

    @Test
    void gecersizTelefonSlugVeyaKisaSifre_olusturmaz() {
        when(tenantRepository.count()).thenReturn(0L);

        assertThat(service(new BootstrapProperties("G", "yilmaz-otomotiv", "A", "12345", "ilk-sifre-123")).run()).isEqualTo(Result.INVALID);
        assertThat(service(new BootstrapProperties("G", "Yılmaz Oto", "A", "05301112233", "ilk-sifre-123")).run()).isEqualTo(Result.INVALID);
        assertThat(service(new BootstrapProperties("G", "yilmaz", "A", "05301112233", "kisa")).run()).isEqualTo(Result.INVALID);

        verify(tenantRepository, never()).save(any());
    }
}
