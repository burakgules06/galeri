package com.galeri.app.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

// vehicleId doluysa arac masrafi (aracin maliyetine eklenir), bossa genel gider.
// Kategori: categoryId ya da categoryName; ad ile gelirse yoksa o an olusturulur.
public record ExpenseRequest(
        @NotNull LocalDate date,
        UUID sourceAccountId,   // kasa / banka; paidByOwner ise bos olabilir
        @NotNull @Positive Long amountCents,
        UUID vehicleId,
        UUID categoryId,
        @Size(max = 60) String categoryName,
        UUID parentCategoryId,
        @Size(max = 500) String description,
        // Sadece konsinye aracta: true ise masraf sahibinin carisine yazilir (sahibi galeriye borclanir),
        // degilse galerinin genel gideridir. Diger araclarda yok sayilir.
        Boolean chargeToOwner,
        // Dukkan gideri cepten (elden) odendi: kasa / banka degismez, kimsenin sermayesi dusmez. Defterde dukkan sahibinin
        // sermaye hesabindan gidere yazilir (sermayesi o kadar artar, gider kadar azalir). Sadece genel giderde.
        Boolean paidByOwner
) {
    public ExpenseRequest(LocalDate date, UUID sourceAccountId, Long amountCents, UUID vehicleId, UUID categoryId, String categoryName,
                          UUID parentCategoryId, String description, Boolean chargeToOwner) {
        this(date, sourceAccountId, amountCents, vehicleId, categoryId, categoryName, parentCategoryId, description, chargeToOwner, null);
    }
}
