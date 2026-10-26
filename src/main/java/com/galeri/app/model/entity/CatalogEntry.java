package com.galeri.app.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Galeriye ozel katalog eklemesi: ana katalogda (vehicles.json) olmayan marka / model, ADMIN onayiyla eklenir.
@Entity
@Table(name = "catalog_entry")
@Getter
@Setter
public class CatalogEntry extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 40)
    private String make;

    @Column(nullable = false, length = 60)
    private String model;
}
