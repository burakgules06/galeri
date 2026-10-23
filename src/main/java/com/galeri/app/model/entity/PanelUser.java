package com.galeri.app.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Panele giris yapan kullanici. SUPER_ADMIN'in tenant'i yoktur (null).
@Entity
@Table(name = "panel_user")
@Getter
@Setter
public class PanelUser extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false, length = 20)
    private String role = "STAFF";
}
