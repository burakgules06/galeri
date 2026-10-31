package com.galeri.app.model.entity;

import com.galeri.app.model.enums.AccountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Defterdeki bir hesap. Bakiye burada saklanmaz, hareketlerden hesaplanir.
@Entity
@Table(name = "account")
@Getter
@Setter
public class Account extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private AccountType type;

    @Column(nullable = false, length = 120)
    private String name;

    // BALANCE icin (kisinin carisi)
    @Column(name = "contact_id", updatable = false)
    private UUID contactId;

    // CAPITAL icin; BALANCE'ta kullanicinin carisi (V22)
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    // VEHICLE icin
    @Column(name = "vehicle_id", updatable = false)
    private UUID vehicleId;

    // CAPITAL icin: ortak kaldirildi (V17)
    @Column(nullable = false)
    private boolean closed = false;
}
