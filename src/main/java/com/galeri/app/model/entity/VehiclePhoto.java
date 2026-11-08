package com.galeri.app.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Arac fotografi. Dosya sunucu diskinde (PhotoStorage); id ayni zamanda dosya adidir ve tahmin edilemez.
@Entity
@Table(name = "vehicle_photo")
@Getter
@Setter
public class VehiclePhoto extends BaseModel {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    // 0 = kapak fotografi
    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;
}
