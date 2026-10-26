package com.galeri.app.model.enums;

// Ortak aracta ortagin katkisi nereden: sermayesinden (nakitteki sermayesi araca baglanir), cebinden (once otomatik
// sermaye girisi, sonra baglanir) ya da ikisi (cepten kisim tutar olarak). Degerler vehicle_share kisitiyla (V10) ayni.
public enum ContributionSource {
    CAPITAL, POCKET, HYBRID
}
