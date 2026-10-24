package com.galeri.app.model.response;

import com.galeri.app.model.enums.LedgerEntryType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
        OffsetDateTime createdAt
) {}
