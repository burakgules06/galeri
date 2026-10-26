package com.galeri.app.service;

// Odeme gunu bildirimleri: gorev her dakika, galerinin sectigi saati kontrol eder (Europe/Istanbul).
public interface PaymentReminderService {

    /**
     * Bugun vadesi gelen ve vadesi 1-3 gun once gecmis odenmemis taksitler icin her galerinin ADMIN'lerine
     * tek, toplu bir bildirim gonderir. Ayni taksit icin ayni gun ikinci bildirim gitmez (notification_log).
     *
     * @return bildirim gonderilen galeri sayisi
     */
    int sendDailyReminders();
}
