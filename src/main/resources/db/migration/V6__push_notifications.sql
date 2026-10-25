-- ============================================================================
-- V6: odeme gunu bildirimleri (web push, bkz. docs/MIMARI.md §9)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- PUSH_SUBSCRIPTION: panel kullanicisinin bildirime izin verdigi her cihaz / tarayici icin bir satir.
-- ----------------------------------------------------------------------------
CREATE TABLE push_subscription (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenant(id),
    panel_user_id  UUID         NOT NULL REFERENCES panel_user(id) ON DELETE CASCADE,
    endpoint       TEXT         NOT NULL UNIQUE,
    p256dh         TEXT         NOT NULL,
    auth           TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_push_subscription_tenant     ON push_subscription(tenant_id);
CREATE INDEX idx_push_subscription_panel_user ON push_subscription(panel_user_id);

-- ----------------------------------------------------------------------------
-- NOTIFICATION_LOG: hangi taksit icin hangi gun bildirim gitti. Ayni taksite ayni gun ikinci bildirim gitmez.
-- Taksit silinirse (satis geri alininca) kaydi da silinir.
-- ----------------------------------------------------------------------------
CREATE TABLE notification_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenant(id),
    installment_id  UUID         NOT NULL REFERENCES installment(id) ON DELETE CASCADE,
    notify_date     DATE         NOT NULL,
    sent_at         TIMESTAMPTZ  NOT NULL,
    UNIQUE (installment_id, notify_date)
);
CREATE INDEX idx_notification_log_tenant ON notification_log(tenant_id);
