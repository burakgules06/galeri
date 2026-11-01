package com.galeri.app.model.request;

import com.galeri.app.model.enums.ContactKind;
import com.galeri.app.model.enums.ContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 20) String phone,
        ContactType type,
        String notes,
        ContactKind kind   // bossa musteri
) {
    public ContactRequest(String name, String phone, ContactType type, String notes) {
        this(name, phone, type, notes, null);
    }
}
