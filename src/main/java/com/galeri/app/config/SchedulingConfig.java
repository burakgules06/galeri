package com.galeri.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Zamanlanmis gorevler (odeme gunu bildirimleri, bkz. PaymentReminderJob).
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
