package com.galeri.app.model.entity;

import com.galeri.app.model.enums.ContributionSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Ortak aracin bir sahibi (V10): contact null ise Havuz (galeri kasasi). Paylar baz puan, aracin sahiplik paylari ve
// kar payi oranlari ayri ayri toplam 10000 (%100). Katki = gercek alis x sahiplik payi; kaynagi sermaye / cep / hibrit.
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

    // Sahip her zaman kullanici (V22): dukkan sahibi ya da sermayedar
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private PanelUser user;

    @Column(name = "share_bp", nullable = false)
    private int shareBp;

    @Column(name = "profit_share_bp", nullable = false)
    private int profitShareBp;

    @Enumerated(EnumType.STRING)
    @Column(name = "contribution_source", nullable = false, length = 7)
    private ContributionSource contributionSource = ContributionSource.CAPITAL;

    @Column(name = "pocket_cents")
    private Long pocketCents;

    /** Dukkan sahibinin payi eski Havuz gibi davranir: parasi kasadan, kari donem karina. */
    public boolean isPool() {
        return user.isOwner();
    }
}
