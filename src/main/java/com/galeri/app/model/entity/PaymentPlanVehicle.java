package com.galeri.app.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Toplu satis (sepet) planindaki arac ve satis bedeli (V14). Sepet plani aracsizdir; araclar buradan bulunur.
@Entity
@Table(name = "payment_plan_vehicle")
@Getter
@Setter
public class PaymentPlanVehicle extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "sale_price_cents", nullable = false, updatable = false)
    private Long salePriceCents;
}
