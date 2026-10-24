package com.galeri.app.model.response;

import com.galeri.app.model.enums.LedgerEntryType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Kisinin cari hesap ozeti. Tutarlar kurus; arti bakiye = bize borclu (alacak), eksi = bizim borcumuz (verecek).
// lines eskiden yeniye, runningCents o satirdan sonraki bakiye.
public record ContactStatement(
        ContactResponse contact,
        long balanceCents,
        List<Line> lines,
        List<InstallmentResponse> openInstallments
) {

    public record Line(UUID entryId, LocalDate date, LedgerEntryType type, String description, VehicleRef vehicle,
                       long amountCents, long runningCents, boolean reversal) {}
}
