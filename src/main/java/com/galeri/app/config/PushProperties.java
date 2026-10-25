package com.galeri.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Web push (VAPID) anahtarlari: VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT. Bossa bildirimler kapali.
@ConfigurationProperties(prefix = "push")
public record PushProperties(String publicKey, String privateKey, String subject) {

    public boolean configured() {
        return publicKey != null && !publicKey.isBlank() && privateKey != null && !privateKey.isBlank();
    }
}
