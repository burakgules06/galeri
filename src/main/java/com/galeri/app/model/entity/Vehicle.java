package com.galeri.app.model.entity;

import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.Ownership;
import com.galeri.app.model.enums.Transmission;
import com.galeri.app.model.enums.VehicleStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

// Tutarlar kurus cinsinden (BIGINT). Para alanlari STAFF'a asla donmez (bkz. VehicleDetailAdmin / VehicleDetail).
@Entity
@Table(name = "vehicle")
@Getter
@Setter
public class Vehicle extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 12)
    private String plate;

    @Column(nullable = false, length = 40)
    private String make;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(name = "model_year", nullable = false)
    private Short modelYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Fuel fuel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private Transmission transmission;

    @Column(name = "mileage_km", nullable = false)
    private Integer mileageKm = 0;

    @Column(length = 30)
    private String color;

    @Column(name = "tramer_cents")
    private Long tramerCents;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private Ownership ownership = Ownership.DEALER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VehicleStatus status = VehicleStatus.IN_STOCK;

    // ---- alis ----
    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private Contact seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bought_by_id")
    private PanelUser boughtBy;

    @Column(name = "actual_purchase_cents")
    private Long actualPurchaseCents;

    @Column(name = "official_purchase_cents")
    private Long officialPurchaseCents;

    // ---- satis ----
    @Column(name = "sale_date")
    private LocalDate saleDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    private Contact buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sold_by_id")
    private PanelUser soldBy;

    @Column(name = "sale_price_cents")
    private Long salePriceCents;
}
