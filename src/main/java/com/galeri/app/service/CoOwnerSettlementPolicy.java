package com.galeri.app.service;

// Ortak aracta ortagin alacagi (kar payi ve donus) ne zaman yazilir. Zamanlama sadece burada: servisler bu siniftan sorar.
// Bugun tek uygulama AT_SALE (satis aninda, alici senetleri beklenmeden; odememe riski havuzda). Galerici
// "taksit odendikce" derse ON_COLLECTION eklenir: tahsilat yapildikca orantili pay yazilir.
public interface CoOwnerSettlementPolicy {

    enum Timing { AT_SALE }

    Timing timing();
}
