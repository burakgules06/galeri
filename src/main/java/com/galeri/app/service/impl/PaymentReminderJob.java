package com.galeri.app.service.impl;

import com.galeri.app.service.PaymentReminderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Her gun 09:00 Europe/Istanbul: odeme gunu bildirimleri. Mantik PaymentReminderService'te (test edilebilir).
@Component
@RequiredArgsConstructor
public class PaymentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderJob.class);

    private final PaymentReminderService reminderService;

    // Saat sadece lokal denemede degistirilir (push.reminder-cron); prod'da varsayilan 09:00
    @Scheduled(cron = "${push.reminder-cron:0 0 9 * * *}", zone = "Europe/Istanbul")
    public void run() {
        try {
            int tenants = reminderService.sendDailyReminders();
            log.info("Gunluk odeme bildirimi tamam: {} galeri", tenants);
        } catch (Exception e) {
            log.error("Gunluk odeme bildirimi basarisiz: {}", e.getMessage(), e);
        }
    }
}
