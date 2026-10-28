package com.galeri.app.model.entity;

import com.galeri.app.model.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// Odeme planinin bir taksiti. Odeninde paidEntryId tahsilat / odeme hareketine baglanir.
// Tutar ve odeyen sonradan degismez (defter tutmaz); sadece vade ve senet no duzenlenir.
@Entity
@Table(name = "installment")
@Getter
@Setter
public class Installment extends BaseModel {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", updatable = false)
    private PaymentPlan plan;

    @Column(nullable = false)
    private Short seq;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private PaymentMethod method;

    @Column(name = "note_no", length = 40)
    private String noteNo;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_contact_id", updatable = false)
    private Contact payer;

    @Column(name = "paid_entry_id")
    private UUID paidEntryId;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    // Ortagin alis bedelinden sermaye ile karsilanan kisim (kurus).
    @Column(name = "capital_cents", nullable = false)
    private long capitalCents;

    // Tahsilat sirasinda sermaye ile kapatilan cariyi azaltma hareketi.
    @Column(name = "capital_entry_id")
    private UUID capitalEntryId;

    // Cek bilgisi (V12): bankasi ve keside eden (cekin asil sahibi, odeyenden farkli olabilir)
    @Column(name = "bank_name", length = 60)
    private String bankName;

    @Column(name = "drawer_name", length = 120)
    private String drawerName;

    // Ciro (V12): alinan cek / senet bu kisiye odeme olarak verildi. paidEntryId: veren -> portfoy, endorseEntryId: portfoy -> alan
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endorsed_to_contact_id")
    private Contact endorsedTo;

    @Column(name = "endorse_entry_id")
    private UUID endorseEntryId;

    // Pesinat (V15): plan kurulurken ayni islemde tahsil / odendi ("simdi alindi")
    @Column(name = "down_payment", nullable = false)
    private boolean downPayment;

    // Evrak sermayedarinsa (V18): odenince yazilan kredi hareketi; odeme geri alininca terslenir
    @Column(name = "holder_entry_id")
    private UUID holderEntryId;

    // Karsiliksiz cikti (V12): odemesi geri alindi, borc yeniden acik
    @Column(name = "bounced_at")
    private OffsetDateTime bouncedAt;

    public boolean isPaid() {
        return paidEntryId != null;
    }

    public boolean isEndorsed() {
        return endorseEntryId != null;
    }
}
