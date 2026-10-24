package com.galeri.app.model.entity;

import com.galeri.app.model.enums.BodyPart;
import com.galeri.app.model.enums.PartCondition;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Hasar semasindaki tek bir parcanin durumu. Kaydi olmayan parca ORIGINAL sayilir.
@Entity
@Table(name = "vehicle_part")
@Getter
@Setter
public class VehiclePart extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BodyPart part;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PartCondition condition;
}
