package com.galeri.app.service.impl;

import com.galeri.app.model.entity.Tenant;
import com.galeri.app.repository.TenantRepository;
import com.galeri.app.support.TestSession;
import jakarta.validation.Validation;
import com.galeri.app.controller.ReminderSettingsController.Setting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReminderSettingsServiceImplTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final ReminderSettingsServiceImpl service = new ReminderSettingsServiceImpl(tenants);

    @AfterEach void close() { TestSession.close(); }

    @Test void sadeceOturumdakiGalerininSaatiDegisir_gunIsaretiTemizlenir() {
        UUID own = UUID.randomUUID(), other = UUID.randomUUID();
        TestSession.open(UUID.randomUUID(), own, "ADMIN");
        Tenant tenant = new Tenant();
        tenant.setId(own);
        tenant.setReminderSentOn(LocalDate.of(2026, 7, 1));
        when(tenants.findById(own)).thenReturn(Optional.of(tenant));
        when(tenants.lockById(own)).thenReturn(Optional.of(tenant));
        assertThat(service.time()).isEqualTo("09:00");
        assertThat(service.update("14:30")).isEqualTo("14:30");
        assertThat(tenant.getReminderTime()).isEqualTo(LocalTime.of(14, 30));
        assertThat(tenant.getReminderSentOn()).isNull();
        verify(tenants, never()).findById(other);
        verify(tenants, never()).lockById(other);
        verify(tenants).save(tenant);
    }

    @Test void saatYalnizcaGecerliSaatVeDakikaOlabilir() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String time : new String[]{"09:00", "00:00", "23:59"}) assertThat(validator.validate(new Setting(time))).isEmpty();
            for (String time : new String[]{"9:00", "24:00", "14:60", "09:00:30", ""}) assertThat(validator.validate(new Setting(time))).isNotEmpty();
            assertThat(validator.validate(new Setting(null))).isNotEmpty();
        }
    }
}
