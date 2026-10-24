package com.galeri.app.model.response;

import java.util.List;
import java.util.UUID;

// Arac maliyeti = gercek alis + araca bagli masraflar (kurus). Kar sadece satilmis aracta hesaplanir.
// alisDefterde: alis odemesi kasa/bankadan deftere yazildi mi.
public record AracMaliyet(
        UUID aracId,
        long alisKurus,
        boolean alisDefterde,
        long masrafKurus,
        long toplamKurus,
        Long satisKurus,
        Long karKurus,
        List<HareketResponse> hareketler
) {}
