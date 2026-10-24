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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_contact_id", updatable = false)
    private Contact payer;

    @Column(name = "paid_entry_id")
    private UUID paidEntryId;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    public boolean isPaid() {
        return paidEntryId != null;
    }
}
