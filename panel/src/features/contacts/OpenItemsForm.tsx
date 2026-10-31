// Gecmis senet / cek aktarimi: kagittan takip edilen eski evrak kisinin carisine aracsiz girilir. Yon: kisi bize borclu
// (alinan senet / cek) ya da biz borcluyuz (verilen). Satirlar acik girer; sonra Odemeler'de tahsil / ode / ciro edilir.
// Alinan evrak bir sermayedara aitse "Kimin" secilir: tahsil edildikce onun sermayesine yazilir.
import { useState } from "react";
import { api } from "../../api/galeri";
import { addMonthsIso, METHOD, tl, tlToCents, todayIso } from "../../shared/format";
import type { ContactResponse, ContactSummary, PaymentMethod, PlanDirection } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { ContactPicker } from "./ContactPicker";
import { NumberInput } from "../../shared/ui/NumberInput";

interface Row { key: number; dueDate: string; amount: string; method: PaymentMethod; noteNo: string; description: string; bankName: string; drawerName: string }

const METHODS: PaymentMethod[] = ["PROMISSORY_NOTE", "CHEQUE", "OPEN_ACCOUNT", "CASH", "TRANSFER"];
let nextKey = 1;
const emptyRow = (dueDate: string, method: PaymentMethod = "PROMISSORY_NOTE"): Row =>
  ({ key: nextKey++, dueDate, amount: "", method, noteNo: "", description: "", bankName: "", drawerName: "" });

// contact verilmezse (Odemeler ekranindan) kisi formun basinda secilir.
export function OpenItemsForm({ contact: fixedContact, onClose, onSaved }: {
  contact?: ContactResponse; onClose: () => void; onSaved: () => void;
}) {
  const contacts = useLoad(() => (fixedContact ? Promise.resolve([] as ContactResponse[]) : api.contacts()), [fixedContact?.id]);
  const [extra, setExtra] = useState<ContactResponse[]>([]);
  const [picked, setPicked] = useState<ContactSummary | null>(null);
  const contact: ContactSummary | null = fixedContact ?? picked;
  const [direction, setDirection] = useState<PlanDirection>("SALE");
  const partners = useLoad(() => api.partners().catch(() => []), []);
  const investors = (partners.data ?? []).filter((p) => !p.owner);
  const [holder, setHolder] = useState("");
  const [rows, setRows] = useState<Row[]>([emptyRow(todayIso())]);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const cents = (r: Row) => { const c = tlToCents(r.amount); return c != null && !Number.isNaN(c) ? c : 0; };
  const filled = rows.filter((r) => r.amount.trim());
  const total = filled.reduce((t, r) => t + cents(r), 0);
  const update = (key: number, change: Partial<Row>) => setRows((list) => list.map((r) => (r.key === key ? { ...r, ...change } : r)));
  const addRow = () => setRows((list) => {
    const last = list[list.length - 1];
    return [...list, emptyRow(last ? addMonthsIso(last.dueDate, 1) : todayIso(), last?.method)];
  });

  const submit = async () => {
    if (filled.length === 0) return setError("En az bir senet, çek ya da açık hesap kaydı gir.");
    if (filled.some((r) => cents(r) <= 0 || !r.dueDate)) return setError("Her satırda vade ve tutar olmalı.");
    setSaving(true);
    setError(null);
    try {
      if (!contact) { setSaving(false); return setError("Kişiyi seç."); }
      await api.addOpenItems(contact.id, {
        direction, date: todayIso(), holderId: direction === "SALE" && holder ? holder : null,
        installments: filled.map((r) => ({
          dueDate: r.dueDate, amountCents: cents(r), method: r.method, noteNo: r.noteNo.trim() || null, payerId: null,
          description: r.method === "OPEN_ACCOUNT" ? r.description.trim() || null : null,
          paidNow: false, accountId: null,
          bankName: r.method === "CHEQUE" ? r.bankName.trim() || null : null,
          drawerName: r.method === "CHEQUE" ? r.drawerName.trim() || null : null,
        })),
      });
      onSaved();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={fixedContact ? `Geçmiş cari kayıt · ${fixedContact.name}` : "Geçmiş cari kayıt"} open onClose={onClose} wide
      footer={<>
        <span className="mr-auto self-center text-[14px] text-murekkep-2">
          {filled.length} kayıt · <b className="sayi text-murekkep text-[16px]">{tl(total)}</b>
        </span>
        <Button variant="secondary" onClick={onClose}>Vazgeç</Button>
        <Button onClick={submit} disabled={saving || filled.length === 0}>{saving ? "Kaydediliyor…" : "Kaydet"}</Button>
      </>}>
      <div className="space-y-4">
        <ErrorBox message={error} />
        {!fixedContact && (
          <Field label="Kişi">
            <ContactPicker contacts={[...extra, ...(contacts.data ?? [])]} selected={picked} onSelect={setPicked}
              onCreated={(c) => setExtra((l) => [c, ...l])} placeholder="İsim yaz ya da seç" />
          </Field>
        )}
        <Segment<PlanDirection> value={direction} onChange={setDirection}
          options={[{ value: "SALE", label: contact ? `${contact.name} bize borçlu` : "Bize borçlu" }, { value: "PURCHASE", label: "Biz borçluyuz" }]} />
        {direction === "SALE" && investors.length > 0 && (
          <Field label="Kimin">
            <Segment<string> value={holder} onChange={setHolder}
              options={[{ value: "", label: "Dükkân" }, ...investors.map((p) => ({ value: p.userId, label: p.name }))]} />
          </Field>
        )}

        <div className="space-y-2">
          {rows.map((r) => (
            <div key={r.key} className="grid grid-cols-2 md:grid-cols-[150px_140px_120px_1fr_36px] gap-2 items-center border-b border-cizgi md:border-0 pb-2 md:pb-0">
              <input type="date" className={inputClass} value={r.dueDate} aria-label="Vade" onChange={(e) => update(r.key, { dueDate: e.target.value })} />
              <NumberInput className={`${inputClass} font-cond text-[17px]`} value={r.amount} aria-label="Tutar" placeholder="100.000"
                preview={false} onValueChange={(amount) => update(r.key, { amount })} />
              <select className={inputClass} value={r.method} aria-label="Tür" onChange={(e) => update(r.key, { method: e.target.value as PaymentMethod })}>
                {METHODS.map((m) => <option key={m} value={m}>{METHOD[m]}</option>)}
              </select>
              <div className="col-span-2 md:col-span-1 grid grid-cols-3 gap-2">
                {r.method !== "OPEN_ACCOUNT" && <input className={`${inputClass} ${r.method === "CHEQUE" ? "" : "col-span-3"}`} value={r.noteNo} aria-label="Numara"
                  placeholder={r.method === "CHEQUE" ? "Çek no" : "Senet no"} onChange={(e) => update(r.key, { noteNo: e.target.value })} />}
                {r.method === "CHEQUE" && <>
                  <input className={inputClass} value={r.bankName} aria-label="Banka" placeholder="Banka" onChange={(e) => update(r.key, { bankName: e.target.value })} />
                  <input className={inputClass} value={r.drawerName} aria-label="Keşideci" placeholder="Keşideci" onChange={(e) => update(r.key, { drawerName: e.target.value })} />
                </>}
                {r.method === "OPEN_ACCOUNT" && <input className="col-span-3" value={r.description} aria-label="Açıklama"
                  maxLength={500} placeholder="Açıklama (isteğe bağlı)" onChange={(e) => update(r.key, { description: e.target.value })} />}
              </div>
              <button type="button" aria-label="Satırı sil" onClick={() => setRows((l) => (l.length > 1 ? l.filter((x) => x.key !== r.key) : l))}
                className="basilir w-9 h-9 rounded-lg hover:bg-yuzey-2 grid place-items-center text-murekkep-3 justify-self-end">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
              </button>
            </div>
          ))}
          <Button type="button" variant="quiet" className="text-plaka" onClick={addRow}>+ Satır ekle</Button>
        </div>
      </div>
    </Modal>
  );
}
