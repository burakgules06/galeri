package com.galeri.app.model.entity;

import com.galeri.app.model.enums.KisiTur;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Musteri, arac aldigimiz kisi, ortak ya da kurum. Rolu araca gore degisir (bkz. docs/MIMARI.md).
@Entity
@Table(name = "kisi")
@Getter
@Setter
public class Kisi extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 120)
    private String ad;

    @Column(length = 20)
    private String telefon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private KisiTur tur = KisiTur.KISI;

    @Column(columnDefinition = "text")
    private String notlar;
}
