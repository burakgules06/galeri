package com.galeri.app.model.response;

import com.galeri.app.model.enums.AccountType;

import java.util.List;
import java.util.UUID;

// Kasa / banka hesabindaki paranin kime ait oldugu. Sermayedarin payi hareketlerden izlenir (sermaye girisi / cekisi o hesaba,
// ortakli aracin alis odemesinde katkisi, satis tahsilatindan payi, evrak tahsilati; hesaplar arasi transferde oraninda tasinir);
// dukkan sahibinin payi hesaptaki paranin kalanidir. Kalan dagitilamayan kisim (or. sermayeye eklenen kar payi) hesaplara bakiyeleri
// oraninda yazilir. parts toplami = balanceCents.
public record AccountHoldings(UUID accountId, String name, AccountType type, long balanceCents, List<Part> parts) {

    public record Part(UUID userId, String name, boolean owner, long cents) {}
}
