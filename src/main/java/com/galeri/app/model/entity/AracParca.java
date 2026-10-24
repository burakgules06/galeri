package com.galeri.app.model.entity;

import com.galeri.app.model.enums.Parca;
import com.galeri.app.model.enums.ParcaDurum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Hasar semasindaki tek bir parcanin durumu. Kaydi olmayan parca ORIJINAL sayilir.
@Entity
@Table(name = "arac_parca")
@Getter
@Setter
public class AracParca extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "arac_id", nullable = false, updatable = false)
    private UUID aracId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Parca parca;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private ParcaDurum durum;
}
