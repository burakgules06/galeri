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

    // Giris kapali kullanicida (or. telefonu olmayan sermayedar) bos olabilir (V22)
    @Column(unique = true, length = 20)
    private String phone;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // ADMIN = dukkan sahibi, INVESTOR = sermayedar, STAFF = calisan (V22; bir kullanicinin tek rolu var)
    @Column(nullable = false, length = 20)
    private String role = "STAFF";

    @Column(name = "can_login", nullable = false)
    private boolean canLogin = true;

    // Calisan arac alim / satimi girebilir mi (V22)
    @Column(name = "can_trade", nullable = false)
    private boolean canTrade = false;

    // Sermayedarin havuzdaki yuzdesi (sadece bilgi) neye gore gosterilir (V16, V22'de kisiden tasindi)
    @Enumerated(EnumType.STRING)
    @Column(name = "capital_base", nullable = false, length = 8)
    private com.galeri.app.model.enums.CapitalBase capitalBase = com.galeri.app.model.enums.CapitalBase.ALL;

    /** Dukkan sahibi: sermayesi havuzdan kalan, arac payi havuz gibi (parasi kasadan). */
    public boolean isOwner() {
        return "ADMIN".equals(role);
    }

    /** Sermayesi olabilen kullanici: dukkan sahibi ya da sermayedar. */
    public boolean isCapitalHolder() {
        return "ADMIN".equals(role) || "INVESTOR".equals(role);
    }
}
