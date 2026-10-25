package com.galeri.app.service;

// Odeme gunu bildirimleri (bkz. docs/MIMARI.md §9). Zamanlanmis gorev her gun 09:00 Europe/Istanbul'da cagirir.
public interface PaymentReminderService {

    /**
     * Bugun vadesi gelen ve vadesi 1-3 gun once gecmis odenmemis taksitler icin her galerinin ADMIN'lerine
     * tek, toplu bir bildirim gonderir. Ayni taksit icin ayni gun ikinci bildirim gitmez (notification_log).
     *
     * @return bildirim gonderilen galeri sayisi
     */
    int sendDailyReminders();
}
