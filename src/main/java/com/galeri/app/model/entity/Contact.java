package com.galeri.app.model.entity;

import com.galeri.app.model.enums.ContactType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Musteri, arac aldigimiz kisi, ortak ya da kurum. Rolu araca gore degisir (bkz. docs/MIMARI.md).
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

    // Sermaye ortaklari arasinda dukkan sahibi (galeri basina en fazla bir)
    @Column(name = "is_owner", nullable = false)
    private boolean owner = false;
}
