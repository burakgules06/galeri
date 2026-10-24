package com.galeri.app.model.entity;

import com.galeri.app.model.enums.HesapTur;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Defterdeki bir hesap. Bakiye burada saklanmaz, hareketlerden hesaplanir.
@Entity
@Table(name = "hesap")
@Getter
@Setter
public class Hesap extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private HesapTur tur;

    @Column(nullable = false, length = 120)
    private String ad;

    // CARI / SERMAYE icin
    @Column(name = "kisi_id", updatable = false)
    private UUID kisiId;

    // ARAC icin
    @Column(name = "arac_id", updatable = false)
    private UUID aracId;
}
