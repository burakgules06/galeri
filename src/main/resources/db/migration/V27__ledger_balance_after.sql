-- Islem sonrasi kasa / banka bakiyesi: her hareket yazilirken o anki bakiyenin fotografi hareketin yanina saklanir.
-- Kaynak ya da hedef hesap kasa / banka degilse ilgili kolon bos kalir. Gercegin kaynagi yine hareketlerin kendisidir.
ALTER TABLE ledger_entry
    ADD COLUMN source_balance_after_cents BIGINT,
    ADD COLUMN target_balance_after_cents BIGINT;

-- Eski hareketler: kayit sirasina (created_at, id) gore yurutulen bakiye. Degismezlik trigger'i sadece bu doldurma icin kapatilir.
ALTER TABLE ledger_entry DISABLE TRIGGER trg_ledger_entry_immutable;

WITH moves AS (
    SELECT e.id, e.created_at, e.source_account_id AS account_id, -e.amount_cents AS delta, 'S' AS side FROM ledger_entry e
    UNION ALL
    SELECT e.id, e.created_at, e.target_account_id, e.amount_cents, 'T' FROM ledger_entry e
), running AS (
    SELECT m.id, m.side,
           sum(m.delta) OVER (PARTITION BY m.account_id ORDER BY m.created_at, m.id ROWS UNBOUNDED PRECEDING) AS balance
    FROM moves m JOIN account a ON a.id = m.account_id
    WHERE a.type IN ('CASH', 'BANK')
)
UPDATE ledger_entry e
SET source_balance_after_cents = (SELECT r.balance FROM running r WHERE r.id = e.id AND r.side = 'S'),
    target_balance_after_cents = (SELECT r.balance FROM running r WHERE r.id = e.id AND r.side = 'T')
WHERE EXISTS (SELECT 1 FROM running r WHERE r.id = e.id);

ALTER TABLE ledger_entry ENABLE TRIGGER trg_ledger_entry_immutable;

-- Yeni hareket: kasa / banka hesabi kilitlenir (ayni anda gelen iki islem sirayla yazilsin, ikisi de dogru bakiyeyi alsin),
-- hesabin o ana kadarki bakiyesine bu hareket eklenip kolona yazilir. Uygulamanin her yazma yolu ve elle eklenen kayitlar dahil.
CREATE FUNCTION ledger_entry_balance_after() RETURNS trigger AS $$
DECLARE
    source_money BOOLEAN;
    target_money BOOLEAN;
BEGIN
    PERFORM 1 FROM account
    WHERE id IN (NEW.source_account_id, NEW.target_account_id) AND type IN ('CASH', 'BANK')
    ORDER BY id FOR UPDATE;

    SELECT type IN ('CASH', 'BANK') INTO source_money FROM account WHERE id = NEW.source_account_id;
    SELECT type IN ('CASH', 'BANK') INTO target_money FROM account WHERE id = NEW.target_account_id;

    IF source_money THEN
        NEW.source_balance_after_cents := ledger_account_balance(NEW.source_account_id) - NEW.amount_cents;
    ELSE
        NEW.source_balance_after_cents := NULL;
    END IF;
    IF target_money THEN
        NEW.target_balance_after_cents := ledger_account_balance(NEW.target_account_id) + NEW.amount_cents;
    ELSE
        NEW.target_balance_after_cents := NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Hesabin bakiyesi: giren - cikan (bu hareket henuz yazilmadan).
CREATE FUNCTION ledger_account_balance(acc UUID) RETURNS BIGINT AS $$
    SELECT coalesce((SELECT sum(amount_cents) FROM ledger_entry WHERE target_account_id = acc), 0)
         - coalesce((SELECT sum(amount_cents) FROM ledger_entry WHERE source_account_id = acc), 0);
$$ LANGUAGE sql STABLE;

CREATE TRIGGER trg_ledger_entry_balance_after
    BEFORE INSERT ON ledger_entry
    FOR EACH ROW EXECUTE FUNCTION ledger_entry_balance_after();
