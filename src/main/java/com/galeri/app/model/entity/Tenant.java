package com.galeri.app.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

// Galeri (isletme): multi-tenancy koku. Diger tum is tablolari tenant_id tasir.
@Entity
@Table(name = "tenant")
@Getter
@Setter
public class Tenant extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 40)
    private String timezone = "Europe/Istanbul";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "subscription_status", nullable = false, length = 20)
    private String subscriptionStatus = "TRIAL";

    @Column(name = "subscription_until")
    private LocalDate subscriptionUntil;

    @Column(nullable = false, length = 20)
    private String plan = "BASIC";
}
