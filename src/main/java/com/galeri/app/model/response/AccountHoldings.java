package com.galeri.app.model.response;

import com.galeri.app.model.enums.AccountType;

import java.util.List;
import java.util.UUID;

// Kasa / banka hesabindaki paranin kime ait oldugu, hareketlerden: sermaye girisi / cekisi yapanin, ortakli aracin alis odemesinde
// ve satis tahsilatinda ortaklarin payi, sermayedar evrakinin tahsilati onun; hesaplar arasi transfer dukkan sahibinin parasini
// tasir. Dukkan sahibinin payi hesaptaki paranin kalanidir. parts toplami = balanceCents. Tahmin ya da oranla dagitma yok.
public record AccountHoldings(UUID accountId, String name, AccountType type, long balanceCents, List<Part> parts) {

    public record Part(UUID userId, String name, boolean owner, long cents) {}

    /** accounts: hesap basina dagilim. unassigned: kasa / bankaya dokunmadan degisen sermaye (sermaye devri gibi), kisi basina. */
    public record Response(List<AccountHoldings> accounts, List<Part> unassigned) {}
}
