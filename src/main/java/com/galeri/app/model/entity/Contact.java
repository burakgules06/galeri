package com.galeri.app.model.entity;

import com.galeri.app.model.enums.ContactKind;
import com.galeri.app.model.enums.ContactType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Dukkan disindaki taraf: musteri, arac aldigimiz kisi, tedarikci ya da kurum (bkz. docs/MIMARI.md §0 Kisiler).
@Entity
@Table(name = "contact")
@Getter
@Setter
public class Contact extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ContactType type = ContactType.PERSON;

    @Column(columnDefinition = "text")
    private String notes;

    // Ana tur: musteri / alis yaptiklarim / tedarikci (V24). Bicim type'ta (bireysel / tuzel).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ContactKind kind = ContactKind.CUSTOMER;
}
