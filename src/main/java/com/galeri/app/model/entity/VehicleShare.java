package com.galeri.app.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Ozel ortakli (CO_OWNED) aracta bir ortagin payi. Paylar baz puan, aracin paylari toplami 10000 (%100).
@Entity
@Table(name = "vehicle_share")
@Getter
@Setter
public class VehicleShare extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @Column(name = "share_bp", nullable = false)
    private int shareBp;
}
