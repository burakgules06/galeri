package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class ShowroomRequests {

    private ShowroomRequests() {}

    /** listingPriceCents bos: vitrinde "Fiyat icin arayin". */
    public record Listing(boolean showInShowroom, @Positive Long listingPriceCents, @Size(max = 4000) String listingDescription) {}

    /** Yeni sira; ilk fotograf kapak olur. Aracin butun fotograflari tam bir kez verilmeli. */
    public record Reorder(@NotNull @Size(max = 20) List<UUID> photoIds) {}

    /** Bos: vitrinde WhatsApp / ara butonu cikmaz. Rakam, bosluk ve + kabul edilir; kaydederken sadece rakam tutulur. */
    public record Settings(@Pattern(regexp = "^[0-9 +()-]{0,20}$") String phone) {}
}
