// Toplu masraf: tarih ve odeme hesabi ustte bir kez secilir, her satirda arac (bossa genel gider), kategori, tutar ve aciklama.
// Satir eklenir / silinir, hepsi tek istekte kaydedilir (POST /api/expenses/bulk; biri hataliysa hicbiri yazilmaz).
import { useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { api } from "../../api/galeri";
import { tl, tlToCents, todayIso } from "../../shared/format";
import type { ExpenseRequest } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";

interface Row { key: number; vehicleId: string; category: string; amount: string; description: string }

let nextKey = 1;
const emptyRow = (vehicleId = ""): Row => ({ key: nextKey++, vehicleId, category: "", amount: "", description: "" });

export function BulkExpenseForm({ onClose, onSaved }: { onClose: () => void; onSaved: (count: number) => void }) {
  const [date, setDate] = useState(todayIso());
  const [accountId, setAccountId] = useState("");
  const [rows, setRows] = useState<Row[]>([emptyRow(), emptyRow(), emptyRow()]);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);

  const accounts = useLoad(() => api.accounts(), []);
  const stock = useLoad(() => api.vehicles("", "IN_STOCK"), []);
  const general = useLoad(() => api.categories("GENERAL"), []);
  const vehicleCats = useLoad(() => api.categories("VEHICLE"), []);
  const selectedAccount = accountId || accounts.data?.[0]?.id || "";

  const filled = rows.filter((r) => r.amount.trim() || r.category.trim());
  const total = useMemo(() => filled.reduce((t, r) => {
    const c = tlToCents(r.amount);
    return t + (c != null && !Number.isNaN(c) && c > 0 ? c : 0);
  }, 0), [filled]);

  const update = (key: number, change: Partial<Row>) => setRows((list) => list.map((r) => (r.key === key ? { ...r, ...change } : r)));
  const remove = (key: number) => setRows((list) => (list.length > 1 ? list.filter((r) => r.key !== key) : [emptyRow()]));
  const addRow = () => {
    setRows((list) => [...list, emptyRow(list[list.length - 1]?.vehicleId ?? "")]);
    // Yeni satirin ilk alanina gec
    setTimeout(() => formRef.current?.querySelector<HTMLSelectElement>("tbody tr:last-child select, [data-row]:last-child select")?.focus(), 0);
  };
  const enterAddsRow = (e: KeyboardEvent, isLast: boolean) => {
    if (e.key === "Enter" && isLast) { e.preventDefault(); addRow(); }
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (filled.length === 0) return setError("En az bir satır doldur.");
    if (!selectedAccount) return setError("Ödemenin çıktığı hesabı seç.");
    const items: ExpenseRequest[] = [];
    for (const [k, r] of filled.entries()) {
      const cents = tlToCents(r.amount);
      if (cents == null || Number.isNaN(cents) || cents <= 0) return setError(`${k + 1}. satırda tutar eksik.`);
      if (!r.category.trim()) return setError(`${k + 1}. satırda kategori eksik.`);
      items.push({
        date, sourceAccountId: selectedAccount, amountCents: cents, vehicleId: r.vehicleId || null,
        categoryId: null, categoryName: r.category.trim(), description: r.description.trim() || null, chargeToOwner: null,
      });
    }
    setSaving(true);
    setError(null);
    try {
      await api.addExpenses(items);
      onSaved(items.length);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="Masraf ekle" open onClose={onClose} wide
      footer={<>
        <span className="mr-auto self-center text-[14px] text-murekkep-2">
          {filled.length} masraf · <b className="sayi text-murekkep text-[16px]">{tl(total)}</b>
        </span>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="bulk-expense-form" disabled={saving || filled.length === 0}>{saving ? "Kaydediliyor…" : "Kaydet"}</Button>
      </>}>
      <form id="bulk-expense-form" ref={formRef} onSubmit={submit} className="space-y-4">
        <ErrorBox message={error} />

        <div className="grid gap-3 md:grid-cols-[160px_1fr]">
          <Field label="Tarih"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} required /></Field>
          <Field label="Nereden ödendi">
            {accounts.data && (
              <Segment<string> value={selectedAccount} onChange={setAccountId}
                options={accounts.data.map((a) => ({ value: a.id, label: a.name }))} />
            )}
          </Field>
        </div>

        <datalist id="expense-cats-general">{(general.data ?? []).map((c) => <option key={c.id} value={c.name} />)}</datalist>
        <datalist id="expense-cats-vehicle">{(vehicleCats.data ?? []).map((c) => <option key={c.id} value={c.name} />)}</datalist>

        <div className="space-y-2">
          <div className="hidden md:grid grid-cols-[1.2fr_1fr_130px_1fr_36px] gap-2 etiket px-0.5">
            <span>Araç</span><span>Kategori</span><span>Tutar</span><span>Açıklama</span><span />
          </div>
          {rows.map((r, k) => {
            const isLast = k === rows.length - 1;
            return (
              <div key={r.key} data-row className="grid grid-cols-2 md:grid-cols-[1.2fr_1fr_130px_1fr_36px] gap-2 items-center border-b border-cizgi md:border-0 pb-2 md:pb-0">
                <select className={`${inputClass} col-span-2 md:col-span-1`} value={r.vehicleId} aria-label="Araç"
                  onChange={(e) => update(r.key, { vehicleId: e.target.value })}>
                  <option value="">Genel gider</option>
                  {(stock.data ?? []).map((v) => <option key={v.id} value={v.id}>{v.plate} · {v.make} {v.model}</option>)}
                </select>
                <input className={inputClass} value={r.category} aria-label="Kategori" placeholder={r.vehicleId ? "Boya, lastik…" : "Kira, maaş…"}
                  list={r.vehicleId ? "expense-cats-vehicle" : "expense-cats-general"}
                  onChange={(e) => update(r.key, { category: e.target.value })} />
                <NumberInput className={`${inputClass} font-cond text-[17px]`} value={r.amount} aria-label="Tutar" placeholder="5.000"
                  preview={false} onValueChange={(amount) => update(r.key, { amount })} onKeyDown={(e) => enterAddsRow(e, isLast)} />
                <input className={inputClass} value={r.description} aria-label="Açıklama" placeholder="Açıklama"
                  onChange={(e) => update(r.key, { description: e.target.value })} onKeyDown={(e) => enterAddsRow(e, isLast)} />
                <button type="button" onClick={() => remove(r.key)} aria-label="Satırı sil"
                  className="basilir w-9 h-9 rounded-lg hover:bg-yuzey-2 grid place-items-center text-murekkep-3 justify-self-end">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
                </button>
              </div>
            );
          })}
          <Button type="button" variant="quiet" className="text-plaka" onClick={addRow}>+ Satır ekle</Button>
        </div>
      </form>
    </Modal>
  );
}
