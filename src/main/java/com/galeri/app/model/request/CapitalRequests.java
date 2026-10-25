package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

// Sermaye ve kasa islemleri istekleri (MIMARI §5). Tutarlar kurus.
public final class CapitalRequests {

    private CapitalRequests() {}

    public enum Direction { IN, OUT }

    /** Kisiyi sermaye ortagi yap (sermayesi 0 ile baslar). */
    public record Partner(@NotNull UUID contactId) {}

    /** IN: sermaye girisi CAPITAL(ortak) -> kasa/banka; OUT: sermaye cekisi kasa/banka -> CAPITAL(ortak). */
    public record Movement(
            @NotNull Direction direction,
            @NotNull UUID accountId,
            @NotNull LocalDate date,
            @NotNull @Positive Long amountCents,
            @Size(max = 500) String description
    ) {}

    /** Kar payini sermayeye ekle: elle girilir (sistem kar dagitmaz). Donem aciklamaya yazilir. */
    public record Allocation(
            @NotNull UUID contactId,
            @NotNull LocalDate date,
            @NotNull @Positive Long amountCents,
            @NotNull LocalDate periodFrom,
            @NotNull LocalDate periodTo
    ) {}

    /** Virman: kasa <-> banka (ya da banka <-> banka). */
    public record Transfer(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @NotNull LocalDate date,
            @NotNull @Positive Long amountCents,
            @Size(max = 500) String description
    ) {}

    public enum OpeningSide { RECEIVABLE, PAYABLE }

    /**
     * Acilis bakiyesi (sisteme geciste, hesap basina bir kez). accountId (kasa / banka) ya da contactId (cari) dolu olur.
     * Caride side: RECEIVABLE = kisi bize borclu, PAYABLE = biz kisiye borcluyuz.
     */
    public record Opening(
            UUID accountId,
            UUID contactId,
            OpeningSide side,
            @NotNull LocalDate date,
            @NotNull @Positive Long amountCents
    ) {}
}
