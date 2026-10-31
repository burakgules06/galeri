package com.galeri.app.model.request;

import com.galeri.app.model.enums.ContributionSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

// Ortak aracin bir sahibi. contactId bos = Havuz. shareBp sahiplik payi (%50 = 5000), toplam 10000.
// profitShareBp kar payi orani (bos = sahiplik payi), toplam 10000. source bos = sermayeden; HYBRID'de pocketCents cepten kisim.
public record ShareRequest(
        UUID userId,   // dukkan sahibi ya da sermayedar kullanici (V22)
        @NotNull @Min(1) @Max(10000) Integer shareBp,
        @Min(0) @Max(10000) Integer profitShareBp,
        ContributionSource source,
        @Positive Long pocketCents
) {
    /** Sadece sahiplik payi: kar payi orani ayni, katki sermayeden. */
    public ShareRequest(UUID userId, Integer shareBp) {
        this(userId, shareBp, null, null, null);
    }

    public int profitShareOrDefault() {
        return profitShareBp == null ? shareBp : profitShareBp;
    }

    public ContributionSource sourceOrDefault() {
        return source == null ? ContributionSource.CAPITAL : source;
    }
}
