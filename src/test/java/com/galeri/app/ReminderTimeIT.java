package com.galeri.app;

import com.galeri.app.model.entity.PushSubscription;
import com.galeri.app.service.PaymentReminderService;
import com.galeri.app.service.PushSender;
import com.galeri.app.service.PushTestService;
import com.galeri.app.service.ReminderSettingsService;
import com.galeri.app.support.TestSession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class ReminderTimeIT {
    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock reminderClock() {
            return Clock.fixed(Instant.parse("2026-07-01T06:00:00Z"), ZoneOffset.UTC);
        }
    }
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PaymentReminderService reminders;
    @Autowired private ReminderSettingsService settings;
    @Autowired private PushTestService pushTest;
    @MockitoBean private PushSender sender;

    @AfterEach void close() { TestSession.close(); }

    @Test void gercekVeritabanindaSaatFiltresi_ayarIzolasyonu_veTestGonderimiLogYazmaz() {
        UUID tenant = UUID.randomUUID(), later = UUID.randomUUID(), user = UUID.randomUUID(), otherUser = UUID.randomUUID();
        jdbc.update("insert into tenant (id, slug, display_name) values (?, ?, 'Bildirim')", tenant, "reminder-" + tenant);
        jdbc.update("insert into tenant (id, slug, display_name, reminder_time) values (?, ?, 'Diger', '14:30')", later, "reminder-" + later);
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Test', 'ADMIN')",
                user, tenant, user.toString().substring(0, 20));
        jdbc.update("insert into panel_user (id, tenant_id, phone, password_hash, full_name, role) values (?, ?, ?, 'x', 'Diger', 'ADMIN')",
                otherUser, tenant, otherUser.toString().substring(0, 20));
        for (UUID id : new UUID[]{user, otherUser}) {
            jdbc.update("insert into push_subscription (id, tenant_id, panel_user_id, endpoint, p256dh, auth) values (?, ?, ?, ?, 'p', 'a')",
                    UUID.randomUUID(), tenant, id, "https://push.example/" + id);
        }
        TestSession.open(user, tenant, "ADMIN");
        when(sender.enabled()).thenReturn(true);
        when(sender.send(any(), anyString(), anyString(), anyString())).thenReturn(true);
        reminders.sendDailyReminders();
        entityManager.flush();
        assertThat(jdbc.queryForObject("select reminder_sent_on from tenant where id = ?", LocalDate.class, tenant)).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(jdbc.queryForObject("select reminder_sent_on from tenant where id = ?", LocalDate.class, later)).isNull();
        clearInvocations(sender);
        long logs = jdbc.queryForObject("select count(*) from notification_log where tenant_id = ?", Long.class, tenant);
        pushTest.send();
        entityManager.flush();
        var device = ArgumentCaptor.forClass(PushSubscription.class);
        verify(sender).send(device.capture(), eq("Galeri"), eq("Test bildirimi: bildirimler çalışıyor."), eq("/#/payments"));
        assertThat(device.getValue().getPanelUser().getId()).isEqualTo(user);
        assertThat(jdbc.queryForObject("select count(*) from notification_log where tenant_id = ?", Long.class, tenant)).isEqualTo(logs);
        assertThat(jdbc.queryForObject("select reminder_sent_on from tenant where id = ?", LocalDate.class, tenant)).isEqualTo(LocalDate.of(2026, 7, 1));
        settings.update("12:45");
        entityManager.flush();
        assertThat(settings.time()).isEqualTo("12:45");
        assertThat(jdbc.queryForObject("select reminder_sent_on from tenant where id = ?", LocalDate.class, tenant)).isNull();
        assertThat(jdbc.queryForObject("select reminder_time::text from tenant where id = ?", String.class, later)).isEqualTo("14:30:00");
    }
}
