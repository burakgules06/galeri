package com.galeri.app.model.entity;

import com.galeri.app.model.enums.MasrafKok;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Masraf kategorisi. Ayri ekrani yok, masraf girerken yazilan adla olusur.
@Entity
@Table(name = "masraf_kategori")
@Getter
@Setter
public class MasrafKategori extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "ust_id")
    private UUID ustId;

    @Column(nullable = false, length = 60)
    private String ad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6, updatable = false)
    private MasrafKok kok;

    @Column(nullable = false)
    private boolean arsivlendi = false;
}
