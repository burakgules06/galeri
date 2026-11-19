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
        // Sadece konsinye aracta (Kasim 2026): masraf her zaman sahibinin sermayesinden duser. Nakitteki parasi yetmiyorsa
        // istek 1061 ile reddedilir; kullanici "eksiye dusecek, devam?" sorusunu onaylarsa true gelir ve eksiye duser.
        Boolean confirmNegative,
        // Cepten (elden) odendi: kasa / banka degismez, defterde kaynak dukkan sahibinin sermaye hesabi. Genel giderde kimsenin
        // sermayesi dusmez (sermayesi o kadar artar, gider kadar azalir); galeri / ortak arac masrafinda arac maliyetine eklenir,
        // dukkan sahibinin sermayesi artar. Konsinye aracta sayilmaz (ayri uc). Sermayedar ve calisan gonderemez.
        Boolean paidByOwner
) {
    public ExpenseRequest(LocalDate date, UUID sourceAccountId, Long amountCents, UUID vehicleId, UUID categoryId, String categoryName,
                          UUID parentCategoryId, String description, Boolean confirmNegative) {
        this(date, sourceAccountId, amountCents, vehicleId, categoryId, categoryName, parentCategoryId, description, confirmNegative, null);
    }
}
