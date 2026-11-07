package com.galeri.app.model.response;

import com.galeri.app.model.enums.LedgerEntryType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// correctedEntryId doluysa bu bir duzeltme (ters) hareketidir; corrected=true ise bu hareket iptal edilmistir.
public record LedgerEntryResponse(
        UUID id,
        LocalDate date,
        LedgerEntryType type,
        AccountSummary source,
        AccountSummary target,
        long amountCents,
        VehicleRef vehicle,
        String category,
        String description,
        UUID correctedEntryId,
        boolean corrected,
        String createdBy,
        OffsetDateTime createdAt,
        // Hareketin ilgili oldugu kullanicilar (sermayesi / carisi, aracin sahipleri, tahsilattan payi olanlar); kimse yoksa
        // dukkan sahibi. Kasa ekraninda kisi filtresi icin; tek hareket donen uclarda bos.
        List<UUID> userIds,
        // Islem sonrasi kasa / banka bakiyesi (o anki fotograf); hesap kasa / banka degilse ya da kullanici bakiye goremiyorsa null
        Long sourceBalanceAfterCents,
        Long targetBalanceAfterCents
) {
    public LedgerEntryResponse(UUID id, LocalDate date, LedgerEntryType type, AccountSummary source, AccountSummary target, long amountCents,
                               VehicleRef vehicle, String category, String description, UUID correctedEntryId, boolean corrected,
                               String createdBy, OffsetDateTime createdAt) {
        this(id, date, type, source, target, amountCents, vehicle, category, description, correctedEntryId, corrected, createdBy, createdAt,
                List.of(), null, null);
    }
}
