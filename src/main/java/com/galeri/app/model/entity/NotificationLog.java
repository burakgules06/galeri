package com.galeri.app.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// Bir taksit icin bir gun bildirim gonderildi. (installment_id, notify_date) tekil: ayni gun ikinci bildirim gitmez.
@Entity
@Table(name = "notification_log")
@Getter
@Setter
public class NotificationLog {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "installment_id", nullable = false, updatable = false)
    private UUID installmentId;

    @Column(name = "notify_date", nullable = false, updatable = false)
    private LocalDate notifyDate;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;
}
