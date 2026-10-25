package com.galeri.app.service;

import com.galeri.app.model.entity.PushSubscription;

// Web push gonderimi. Is mantigi (PaymentReminderService) kutuphaneden bagimsiz test edilsin diye ayri.
public interface PushSender {

    /** VAPID anahtarlari tanimli ve gonderici hazir mi. */
    boolean enabled();

    /** Tek bir aboneye bildirim. Hata firlatmaz; gonderildiyse true. Gecersiz (404/410) abonelik silinir. */
    boolean send(PushSubscription subscription, String title, String body, String url);
}
