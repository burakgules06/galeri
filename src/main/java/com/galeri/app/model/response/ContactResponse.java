package com.galeri.app.model.response;

import com.galeri.app.model.enums.ContactKind;
import com.galeri.app.model.enums.ContactType;

import java.util.List;

import java.util.UUID;

// tags: islemlerden otomatik (SOLD_TO "Araç sattık", BOUGHT_FROM "Araç aldık"); ana tur (kind) degismez.
public record ContactResponse(UUID id, String name, String phone, ContactType type, String notes, ContactKind kind, List<String> tags) {
    public ContactResponse(UUID id, String name, String phone, ContactType type, String notes) {
        this(id, name, phone, type, notes, ContactKind.CUSTOMER, List.of());
    }
}
