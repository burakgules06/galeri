package com.galeri.app.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Aracin tek bir donanimi (V36). Ad serbest metin; hazir liste panelde. Liste her kayitta bastan yazilir.
@Entity
@Table(name = "vehicle_equipment")
@Getter
@Setter
public class VehicleEquipment extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private int position;
}
