package com.galeri.app.model.entity;

import com.galeri.app.model.enums.LedgerEntryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// Defter kaydi: kaynak hesaptan hedef hesaba tutar (kurus). Silinmez, degismez (DB trigger'i da reddeder).
// Duzeltme: ayni tutarda ters yonde yeni hareket, correctedEntryId ile baglanir.
@Entity
@Immutable
@Table(name = "ledger_entry")
@Getter
@Setter
public class LedgerEntry {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "entry_date", nullable = false, updatable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private LedgerEntryType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", updatable = false)
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_account_id", updatable = false)
    private Account targetAccount;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private Long amountCents;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", updatable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_category_id", updatable = false)
    private ExpenseCategory category;

    @Column(length = 500, updatable = false)
    private String description;

    @Column(name = "corrected_entry_id", updatable = false)
    private UUID correctedEntryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", updatable = false)
    private PanelUser createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
