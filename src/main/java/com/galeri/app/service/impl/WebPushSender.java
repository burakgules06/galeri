package com.galeri.app.service.impl;

import com.galeri.app.config.PushProperties;
import com.galeri.app.model.entity.PushSubscription;
import com.galeri.app.repository.PushSubscriptionRepository;
import com.galeri.app.service.PushSender;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Security;

// nl.martijndwars web-push ile VAPID gonderimi (Akilli Randevum'daki PushNotificationServiceImpl ile ayni yaklasim).
// Bildirim hicbir is akisini bozmamali: burasi hata firlatmaz, sadece loglar.
@Service
public class WebPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    private final PushProperties props;
    private final PushSubscriptionRepository subscriptionRepository;
    private PushService pushService;

    public WebPushSender(PushProperties props, PushSubscriptionRepository subscriptionRepository) {
        this.props = props;
        this.subscriptionRepository = subscriptionRepository;
    }

    @PostConstruct
    void init() {
        if (!props.configured()) {
            log.warn("Push bildirimleri kapali: VAPID anahtarlari tanimli degil");
            return;
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            pushService = new PushService(props.publicKey(), props.privateKey(), props.subject());
        } catch (Exception e) {
            log.error("PushService baslatilamadi: {}", e.getMessage());
        }
    }

    @Override
    public boolean enabled() {
        return pushService != null;
    }

    @Override
    public boolean send(PushSubscription sub, String title, String body, String url) {
        if (pushService == null) return false;
        try {
            Subscription subscription = new Subscription(sub.getEndpoint(), new Subscription.Keys(sub.getP256dh(), sub.getAuth()));
            // aes128gcm standart sifreleme; Safari/iOS eski aesgcm'yi kabul etmez
            HttpResponse response = pushService.send(new Notification(subscription, payload(title, body, url)), Encoding.AES128GCM);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                subscriptionRepository.deleteByEndpoint(sub.getEndpoint());
                log.info("Push aboneligi gecersiz, silindi: kullanici {}", sub.getPanelUser().getId());
                return false;
            }
            if (status >= 400) {
                log.warn("Push gonderilemedi ({}): kullanici {}", status, sub.getPanelUser().getId());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Push gonderilemedi: kullanici {} | hata: {}", sub.getPanelUser().getId(), e.getMessage());
            return false;
        }
    }

    /** Service worker'in okudugu JSON: { title, body, url }. */
    static String payload(String title, String body, String url) {
        return "{\"title\":\"" + json(title) + "\",\"body\":\"" + json(body) + "\",\"url\":\"" + json(url) + "\"}";
    }

    private static String json(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }
}
