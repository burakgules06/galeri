// Satis: alici, bedel ve odeme plani. Hazir secenekler satirlari uretir, satirlar sonra duzenlenebilir.
// Altta her zaman "Planlanan / Kalan"; kalan 0 olmadan kaydedilmez. Pesinat ("simdi alindi") ayni islemde kasaya girer.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { addMonthsIso, centsToInput, METHOD, tl, tlToCents, todayIso } from "../../shared/format";
import type { AccountResponse, ContactResponse, ContactSummary, PaymentMethod, VehicleDetailAdmin } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { ContactPicker } from "../contacts/ContactPicker";
import {
  cashPlan, installmentPlan, institutionPlan, row, rowProblem, toRequest, type PlanMode, type PlanRow,
} from "./salePlan";

interface Props { open: boolean; onClose: () => void; vehicle: VehicleDetailAdmin; onSaved: (d: VehicleDetailAdmin) => void }

const MODES: { value: PlanMode; label: string }[] = [
  { value: "CASH", label: "Peşin" },
  { value: "INSTALLMENTS", label: "Taksit / senet" },
  { value: "INSTITUTION", label: "Evim / kredi" },
  { value: "MIXED", label: "Karma" },
];

export function SaleForm(props: Props) {
  return props.open ? <SaleFormContent {...props} /> : null;
}

function SaleFormContent({ onClose, vehicle, onSaved }: Props) {
  const [date, setDate] = useState(todayIso());
  const [buyer, setBuyer] = useState<ContactSummary | null>(null);
  const [soldBy, setSoldBy] = useState("");
  const [priceText, setPriceText] = useState("");
  const [mode, setMode] = useState<PlanMode | null>(null);
  const [rows, setRows] = useState<PlanRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const contacts = useLoad(() => api.contacts(), []);
  const staff = useLoad(() => api.staff(), []);
  const accounts = useLoad(() => api.accounts(), []);
  const [extraContacts, setExtraContacts] = useState<ContactResponse[]>([]);
  const allContacts = [...extraContacts, ...(contacts.data ?? [])];
  const organizations = allContacts.filter((c) => c.type === "ORGANIZATION");

  const parsed = tlToCents(priceText);
  const price = parsed != null && !Number.isNaN(parsed) ? parsed : 0;
  const planned = rows.reduce((t, r) => t + (r.amountCents > 0 ? r.amountCents : 0), 0);
  const remaining = price - planned;
  const firstAccount = accounts.data?.[0]?.id ?? "";
  const problems = rows.map(rowProblem);
  const canSave = !!buyer && price > 0 && rows.length > 0 && remaining === 0 && problems.every((p) => p == null);

  const chooseMode = (m: PlanMode) => {
    setMode(m);
    if (m === "CASH") setRows(cashPlan(price, date, firstAccount));
    if (m === "MIXED" && rows.length === 0 && price > 0) setRows([row({ dueDate: date, amountCents: price })]);
  };

  const changePrice = (text: string) => {
    setPriceText(text);
    // Pesin planda bedel degisince tek satir da degissin (elle duzenlenen planlara dokunulmaz)
    const cents = tlToCents(text);
    if (mode === "CASH" && cents != null && !Number.isNaN(cents)) setRows(cashPlan(cents, date, rows[0]?.accountId || firstAccount));
  };

  const updateRow = (key: number, patch: Partial<PlanRow>) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  const removeRow = (key: number) => setRows((rs) => rs.filter((r) => r.key !== key));
  const addRow = () => setRows((rs) => [...rs, row({ dueDate: date, amountCents: Math.max(0, remaining) })]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!buyer) return setError("Alıcıyı seç.");
    if (!canSave) return setError("Planı tamamla: kalan 0 olmalı ve her satır dolu olmalı.");
    setSaving(true);
    setError(null);
    try {
      onSaved(await api.sell(vehicle.vehicle.id, {
        saleDate: date, buyerId: buyer.id, soldById: soldBy || null, salePriceCents: price, installments: rows.map(toRequest),
      }));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="Satışı kaydet" open onClose={onClose} wide
      footer={
        <div className="w-full flex flex-wrap items-center justify-between gap-3">
          <p className="text-[14px] text-murekkep-2">
            Planlanan <b className="sayi text-[17px] text-murekkep">{tl(planned)}</b>
            <span className="mx-2 text-murekkep-3">·</span>
            Kalan <b className={`sayi text-[17px] ${remaining === 0 && price > 0 ? "text-basari" : remaining < 0 ? "text-tehlike" : "text-uyari"}`}>{tl(remaining)}</b>
          </p>
          <div className="flex gap-2">
            <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
            <Button type="submit" form="sale-form" disabled={saving || !canSave}>{saving ? "Kaydediliyor…" : "Satışı kaydet"}</Button>
          </div>
        </div>
      }>
      <form id="sale-form" onSubmit={submit} className="space-y-5">
        <ErrorBox message={error} />

        <section className="grid gap-3 sm:grid-cols-2">
          <Field label="Alıcı" hint="Zorunlu">
            <ContactPicker contacts={allContacts} selected={buyer} onSelect={setBuyer}
              onCreated={(c) => setExtraContacts((l) => [c, ...l])} placeholder="İsim yaz ya da seç" />
          </Field>
          <Field label="Satış bedeli" hint="TL">
            <input className={`${inputClass} sayi text-[19px] font-semibold`} value={priceText} onChange={(e) => changePrice(e.target.value)}
              inputMode="decimal" placeholder="1.400.000" />
          </Field>
          <Field label="Satış tarihi">
            <input type="date" className={inputClass} value={date} min={vehicle.vehicle.purchaseDate} onChange={(e) => setDate(e.target.value)} required />
          </Field>
          <Field label="Satan çalışan">
            <select className={inputClass} value={soldBy} onChange={(e) => setSoldBy(e.target.value)}>
              <option value="">Seçilmedi</option>
              {(staff.data ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
        </section>

        <section className="space-y-3">
          <p className="etiket">Ödeme planı</p>
          <Segment<PlanMode> value={mode ?? ("" as PlanMode)} onChange={chooseMode} options={MODES} />
          {mode === "INSTALLMENTS" && (
            <InstallmentBuilder price={price} saleDate={date} account={firstAccount} onBuild={setRows} />
          )}
          {mode === "INSTITUTION" && (
            <InstitutionBuilder price={price} saleDate={date} account={firstAccount} organizations={organizations} onBuild={setRows} />
          )}
          {mode == null && <p className="text-[13.5px] text-murekkep-3">Bir seçenek seç; satırlar oluşur, sonra düzenleyebilirsin.</p>}
        </section>

        {rows.length > 0 && (
          <section className="space-y-2">
            <div className="hidden md:grid grid-cols-[140px_150px_140px_1fr_40px] gap-2 px-1 etiket">
              <span>Vade</span><span>Tutar</span><span>Yöntem</span><span>Ayrıntı</span><span />
            </div>
            {rows.map((r, k) => (
              <RowEditor key={r.key} r={r} problem={problems[k]} accounts={accounts.data ?? []} organizations={organizations}
                onChange={(patch) => updateRow(r.key, patch)} onRemove={() => removeRow(r.key)} />
            ))}
          </section>
        )}
        {mode != null && (
          <Button type="button" variant="secondary" onClick={addRow} disabled={price <= 0}>+ Satır ekle</Button>
        )}
      </form>
    </Modal>
  );
}

function InstallmentBuilder({ price, saleDate, account, onBuild }: {
  price: number; saleDate: string; account: string; onBuild: (rows: PlanRow[]) => void;
}) {
  const [down, setDown] = useState("");
  const [count, setCount] = useState("6");
  const [firstDue, setFirstDue] = useState(addMonthsIso(saleDate, 1));
  const [method, setMethod] = useState<PaymentMethod>("PROMISSORY_NOTE");
  const downCents = tlToCents(down) ?? 0;
  const n = Number(count);
  const valid = price > 0 && !Number.isNaN(downCents) && downCents < price && Number.isInteger(n) && n >= 1 && n <= 60;

  return (
    <div className="rounded-[12px] bg-yuzey-2 border border-cizgi p-3 space-y-3">
      <div className="grid gap-3 grid-cols-2 md:grid-cols-4">
        <Field label="Peşinat" hint="Şimdi alınır"><input className={inputClass} value={down} onChange={(e) => setDown(e.target.value)} inputMode="decimal" placeholder="300.000" /></Field>
        <Field label="Taksit sayısı"><input className={inputClass} value={count} onChange={(e) => setCount(e.target.value)} inputMode="numeric" /></Field>
        <Field label="İlk vade"><input type="date" className={inputClass} value={firstDue} onChange={(e) => setFirstDue(e.target.value)} /></Field>
        <Field label="Yöntem">
          <select className={inputClass} value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
            <option value="PROMISSORY_NOTE">Senet</option>
            <option value="TRANSFER">Havale</option>
            <option value="CASH">Nakit</option>
          </select>
        </Field>
      </div>
      <div className="flex items-center justify-between gap-2">
        <p className="text-[13px] text-murekkep-2">
          {valid ? <>Kalan {tl(price - downCents)} → {n} × ~{tl(Math.floor((price - downCents) / n))}, aylık</> : "Önce satış bedelini gir."}
        </p>
        <Button type="button" onClick={() => onBuild(installmentPlan(price, downCents, n, firstDue, method, saleDate, account))} disabled={!valid}>Planı kur</Button>
      </div>
    </div>
  );
}

function InstitutionBuilder({ price, saleDate, account, organizations, onBuild }: {
  price: number; saleDate: string; account: string; organizations: ContactResponse[]; onBuild: (rows: PlanRow[]) => void;
}) {
  const [down, setDown] = useState("");
  const [orgId, setOrgId] = useState("");
  const [due, setDue] = useState(saleDate);
  const downCents = tlToCents(down) ?? 0;
  const org = organizations.find((o) => o.id === orgId) ?? null;
  const valid = price > 0 && !Number.isNaN(downCents) && downCents < price && !!org;

  return (
    <div className="rounded-[12px] bg-yuzey-2 border border-cizgi p-3 space-y-3">
      <div className="grid gap-3 grid-cols-2 md:grid-cols-3">
        <Field label="Peşinat (alıcıdan)" hint="Şimdi alınır"><input className={inputClass} value={down} onChange={(e) => setDown(e.target.value)} inputMode="decimal" placeholder="290.000" /></Field>
        <Field label="Kurum" hint={organizations.length ? undefined : "Kişiler'de kurum ekle"}>
          <select className={inputClass} value={orgId} onChange={(e) => setOrgId(e.target.value)}>
            <option value="">Seç</option>
            {organizations.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
          </select>
        </Field>
        <Field label="Kurumun ödeme tarihi"><input type="date" className={inputClass} value={due} onChange={(e) => setDue(e.target.value)} /></Field>
      </div>
      <div className="flex items-center justify-between gap-2">
        <p className="text-[13px] text-murekkep-2">{valid ? <>Kurumdan {tl(price - downCents)}; bu kısım {org?.name} carisine yazılır.</> : "Bedel ve kurum gerekli."}</p>
        <Button type="button" disabled={!valid}
          onClick={() => onBuild(institutionPlan(price, downCents, org && { id: org.id, name: org.name, phone: org.phone }, due, saleDate, account))}>
          Planı kur
        </Button>
      </div>
    </div>
  );
}

function RowEditor({ r, problem, accounts, organizations, onChange, onRemove }: {
  r: PlanRow; problem: string | null; accounts: AccountResponse[]; organizations: ContactResponse[];
  onChange: (patch: Partial<PlanRow>) => void; onRemove: () => void;
}) {
  const [text, setText] = useState(centsToInput(r.amountCents));
  const changeAmount = (t: string) => {
    setText(t);
    const cents = tlToCents(t);
    onChange({ amountCents: cents != null && !Number.isNaN(cents) ? cents : 0 });
  };

  return (
    <div className={`rounded-[12px] border p-2.5 md:p-2 grid gap-2 grid-cols-2 md:grid-cols-[140px_150px_140px_1fr_40px] md:items-center ${
      problem ? "border-uyari bg-uyari-zemin/40" : r.paidNow ? "border-basari/30 bg-basari-zemin/50" : "border-cizgi bg-yuzey"}`}>
      <input type="date" className={`${inputClass} h-9`} value={r.dueDate} onChange={(e) => onChange({ dueDate: e.target.value })} aria-label="Vade" />
      <input className={`${inputClass} h-9 sayi text-[16px]`} value={text} onChange={(e) => changeAmount(e.target.value)} inputMode="decimal" aria-label="Tutar" />
      <select className={`${inputClass} h-9`} value={r.method} aria-label="Yöntem"
        onChange={(e) => onChange({ method: e.target.value as PaymentMethod, payer: null })}>
        {(Object.keys(METHOD) as PaymentMethod[]).map((m) => <option key={m} value={m}>{METHOD[m]}</option>)}
      </select>
      <div className="col-span-2 md:col-span-1 flex flex-wrap items-center gap-2 min-w-0">
        {r.method === "INSTITUTION" && (
          <select className={`${inputClass} h-9 md:w-40`} value={r.payer?.id ?? ""} aria-label="Kurum"
            onChange={(e) => { const o = organizations.find((x) => x.id === e.target.value); onChange({ payer: o ? { id: o.id, name: o.name, phone: o.phone } : null }); }}>
            <option value="">Kurum seç</option>
            {organizations.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
          </select>
        )}
        {r.method === "PROMISSORY_NOTE" && (
          <input className={`${inputClass} h-9 md:w-32`} value={r.noteNo} onChange={(e) => onChange({ noteNo: e.target.value })} placeholder="Senet no" aria-label="Senet no" />
        )}
        <label className="inline-flex items-center gap-1.5 text-[13.5px] font-semibold whitespace-nowrap">
          <input type="checkbox" checked={r.paidNow} className="w-4 h-4 accent-[var(--color-basari)]"
            onChange={(e) => onChange({ paidNow: e.target.checked, accountId: e.target.checked ? (r.accountId || accounts[0]?.id || "") : "" })} />
          Şimdi alındı
        </label>
        {r.paidNow && (
          <select className={`${inputClass} h-9 md:w-36`} value={r.accountId} onChange={(e) => onChange({ accountId: e.target.value })} aria-label="Hesap">
            {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
        )}
        {problem && <span className="text-[12.5px] text-uyari font-semibold">{problem}</span>}
      </div>
      <button type="button" onClick={onRemove} aria-label="Satırı sil"
        className="basilir justify-self-end w-9 h-9 rounded-lg grid place-items-center text-murekkep-3 hover:bg-tehlike-zemin hover:text-tehlike">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
      </button>
    </div>
  );
}
