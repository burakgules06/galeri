package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Arac alisinda: userId'nin nakdi katkisina yetmezse eksik kadar dukkan sahibi borc verir (OwnerLoan gibi, ayni kayitta). */
public record ShortfallLoan(@NotNull UUID userId, @NotNull UUID accountId, boolean pocket) {

    public OwnerLoan loan() {
        return new OwnerLoan(accountId, pocket);
    }
}
