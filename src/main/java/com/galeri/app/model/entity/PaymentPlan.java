package com.galeri.app.model.entity;

import com.galeri.app.model.enums.PlanDirection;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Aracin satis ya da alis odeme plani; aracsiz plan kisinin gecmis senet / cekleridir (V13). Para hareketi burada degil defterde; plan sadece "ne zaman, kimden, ne kadar".
@Entity
@Table(name = "payment_plan")
@Getter
@Setter
public class PaymentPlan extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    // Bos: aracsiz plan (gecmis senet / cek aktarimi, V13)
    @Column(name = "vehicle_id", updatable = false)
    private UUID vehicleId;

    // Aracsiz planda plan toplamini cariye yazan acilis hareketi (V13)
    @Column(name = "opening_entry_id", updatable = false)
    private UUID openingEntryId;

    // Gecmis alacakta evrak bir sermayedara aitse (V18): tahsil edildikce onun sermayesine yazilir
    @Column(name = "holder_contact_id", updatable = false)
    private UUID holderContactId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private PlanDirection direction;

    // Karsi taraf: satista alici, alista satici
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Column(name = "total_cents", nullable = false)
    private Long totalCents;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", updatable = false)
    private PanelUser createdBy;
}
