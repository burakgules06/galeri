package com.galeri.app.service.impl;

import com.galeri.app.service.PaymentReminderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Her dakika saati gelen galerilerin odeme bildirimleri kontrol edilir.
@Component
@RequiredArgsConstructor
public class PaymentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderJob.class);

    private final PaymentReminderService reminderService;

    @Scheduled(cron = "0 * * * * *", zone = "Europe/Istanbul")
    public void run() {
        try {
            int tenants = reminderService.sendDailyReminders();
            log.info("Gunluk odeme bildirimi tamam: {} galeri", tenants);
        } catch (Exception e) {
            log.error("Gunluk odeme bildirimi basarisiz: {}", e.getMessage(), e);
        }
    }
}
