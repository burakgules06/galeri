// Odeme plani kurucusu: hazir secenekler satirlari uretir, satirlar sonra duzenlenebilir. Satis formu ve
// arac formundaki planli alis ayni bileseni kullanir; fark sadece etiketler ve izin verilen secenekler.
import { useState } from "react";
import { addMonthsIso, centsToInput, METHOD, tl, tlToCents } from "../../shared/format";
import type { AccountResponse, ContactResponse, ContactSummary, PaymentMethod } from "../../shared/types";
import { ContactPicker } from "../contacts/ContactPicker";
import { Button, Field, inputClass, Segment } from "../../shared/ui/Common";
import { cashPlan, installmentPlan, institutionPlan, row, rowProblem, type PlanMode, type PlanRow } from "./plan";
import { NumberInput } from "../../shared/ui/NumberInput";

const MODE_LABEL: Record<PlanMode, string> = {
  CASH: "Peşin", INSTALLMENTS: "Taksit / senet", INSTITUTION: "Evim / kredi", MIXED: "Karma",
};

export function PlanBuilder({
  baseCents, date, mode, onMode, rows, onRows, remaining, accounts, organizations, modes, methods, paidNowLabel, downHint,
}: {
  baseCents: number;                 // plana dagitilacak tutar (bedel - odenmis taksitler)
  date: string;                      // satis / alis gunu: pesin ve pesinat vadesi
  mode: PlanMode | null;
  onMode: (m: PlanMode) => void;
  rows: PlanRow[];
  onRows: (rows: PlanRow[]) => void;
  remaining: number;
  accounts: AccountResponse[];
  organizations: ContactResponse[];
  modes: PlanMode[];
  methods: PaymentMethod[];
  paidNowLabel: string;              // "Şimdi alındı" / "Şimdi ödendi"
  downHint: string;                  // "Şimdi alınır" / "Şimdi ödenir"
}) {
  const firstAccount = accounts[0]?.id ?? "";
  const problems = rows.map(rowProblem);
  // "+ Yeni kurum" ile eklenenler hemen listede olsun (form kapanmaz)
  const [newOrgs, setNewOrgs] = useState<ContactResponse[]>([]);
  const orgs = [...newOrgs, ...organizations.filter((o) => !newOrgs.some((n) => n.id === o.id))];
  const orgCreated = (c: ContactResponse) => setNewOrgs((l) => [c, ...l]);

  const choose = (m: PlanMode) => {
    onMode(m);
    if (m === "CASH") onRows(cashPlan(baseCents, date, firstAccount));
    if (m === "MIXED" && rows.length === 0 && baseCents > 0) onRows([row({ dueDate: date, amountCents: baseCents })]);
  };
  const update = (key: number, patch: Partial<PlanRow>) => onRows(rows.map((r) => (r.key === key ? { ...r, ...patch } : r)));

  return (
    <div className="space-y-3">
      <Segment<PlanMode> value={mode ?? ("" as PlanMode)} onChange={choose} options={modes.map((m) => ({ value: m, label: MODE_LABEL[m] }))} />
      {mode === "INSTALLMENTS" && (
        <InstallmentGenerator price={baseCents} date={date} account={firstAccount} methods={methods} downHint={downHint} onBuild={onRows} />
      )}
      {mode === "INSTITUTION" && (
        <InstitutionGenerator price={baseCents} date={date} account={firstAccount} organizations={orgs} onOrgCreated={orgCreated} downHint={downHint} onBuild={onRows} />
      )}
      {mode == null && <p className="text-[13.5px] text-murekkep-3">Bir seçenek seç; satırlar oluşur, sonra düzenleyebilirsin.</p>}

      {rows.length > 0 && (
        <div className="space-y-2">
          <div className="hidden md:grid grid-cols-[140px_150px_140px_1fr_40px] gap-2 px-1 etiket">
            <span>Vade</span><span>Tutar</span><span>Yöntem</span><span>Ayrıntı</span><span />
          </div>
          {rows.map((r, k) => (
            <RowEditor key={r.key} r={r} problem={problems[k]} accounts={accounts} organizations={orgs} onOrgCreated={orgCreated} methods={methods}
              paidNowLabel={paidNowLabel} onChange={(patch) => update(r.key, patch)} onRemove={() => onRows(rows.filter((x) => x.key !== r.key))} />
          ))}
        </div>
      )}
      {mode != null && (
        <Button type="button" variant="secondary" disabled={baseCents <= 0}
          onClick={() => onRows([...rows, row({ dueDate: date, amountCents: Math.max(0, remaining) })])}>
          + Satır ekle
        </Button>
      )}
    </div>
  );
}

/** "Planlanan X TL · Kalan Y TL": kalan 0 ise yesil, eksiye duserse kirmizi, aksi halde turuncu. */
export function PlanSummary({ planned, remaining, ready }: { planned: number; remaining: number; ready: boolean }) {
  return (
    <p className="text-[14px] text-murekkep-2">
      Planlanan <b className="sayi text-[17px] text-murekkep">{tl(planned)}</b>
      <span className="mx-2 text-murekkep-3">·</span>
      Kalan <b className={`sayi text-[17px] ${remaining === 0 && ready ? "text-basari" : remaining < 0 ? "text-tehlike" : "text-uyari"}`}>{tl(remaining)}</b>
    </p>
  );
}

function InstallmentGenerator({ price, date, account, methods, downHint, onBuild }: {
  price: number; date: string; account: string; methods: PaymentMethod[]; downHint: string; onBuild: (rows: PlanRow[]) => void;
}) {
  const [down, setDown] = useState("");
  const [count, setCount] = useState("6");
  const [firstDue, setFirstDue] = useState(addMonthsIso(date, 1));
  const [method, setMethod] = useState<PaymentMethod>("PROMISSORY_NOTE");
  const downCents = tlToCents(down) ?? 0;
  const n = Number(count);
  const valid = price > 0 && !Number.isNaN(downCents) && downCents < price && Number.isInteger(n) && n >= 1 && n <= 60;

  return (
    <div className="rounded-[12px] bg-yuzey-2 border border-cizgi p-3 space-y-3">
      <div className="grid gap-3 grid-cols-2 md:grid-cols-4">
        <Field label="Peşinat" hint={downHint}><NumberInput value={down} onValueChange={setDown} placeholder="300.000" /></Field>
        <Field label="Taksit sayısı"><NumberInput kind="integer" value={count} onValueChange={setCount} /></Field>
        <Field label="İlk vade"><input type="date" className={inputClass} value={firstDue} onChange={(e) => setFirstDue(e.target.value)} /></Field>
        <Field label="Yöntem">
          <select className={inputClass} value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
            {methods.filter((m) => m !== "INSTITUTION").map((m) => <option key={m} value={m}>{METHOD[m]}</option>)}
          </select>
        </Field>
      </div>
      <div className="flex items-center justify-between gap-2">
        <p className="text-[13px] text-murekkep-2">
          {valid ? <>Kalan {tl(price - downCents)} → {n} × ~{tl(Math.floor((price - downCents) / n))}, aylık</> : "Önce bedeli gir."}
        </p>
        <Button type="button" onClick={() => onBuild(installmentPlan(price, downCents, n, firstDue, method, date, account))} disabled={!valid}>Planı kur</Button>
      </div>
    </div>
  );
}

function InstitutionGenerator({ price, date, account, organizations, onOrgCreated, downHint, onBuild }: {
  price: number; date: string; account: string; organizations: ContactResponse[]; onOrgCreated: (c: ContactResponse) => void;
  downHint: string; onBuild: (rows: PlanRow[]) => void;
}) {
  const [down, setDown] = useState("");
  const [org, setOrg] = useState<ContactSummary | null>(null);
  const [due, setDue] = useState(date);
  const downCents = tlToCents(down) ?? 0;
  const valid = price > 0 && !Number.isNaN(downCents) && downCents < price && !!org;

  return (
    <div className="rounded-[12px] bg-yuzey-2 border border-cizgi p-3 space-y-3">
      <div className="grid gap-3 grid-cols-2 md:grid-cols-3">
        <Field label="Peşinat (alıcıdan)" hint={downHint}><NumberInput value={down} onValueChange={setDown} placeholder="290.000" /></Field>
        <Field label="Kurum">
          <ContactPicker kind="ORGANIZATION" contacts={organizations} selected={org} onSelect={setOrg} onCreated={onOrgCreated} placeholder="Evim şirketi, banka…" />
        </Field>
        <Field label="Kurumun ödeme tarihi"><input type="date" className={inputClass} value={due} onChange={(e) => setDue(e.target.value)} /></Field>
      </div>
      <div className="flex items-center justify-between gap-2">
        <p className="text-[13px] text-murekkep-2">{valid ? <>Kurumdan {tl(price - downCents)}; bu kısım {org?.name} carisine yazılır.</> : "Bedel ve kurum gerekli."}</p>
        <Button type="button" disabled={!valid}
          onClick={() => onBuild(institutionPlan(price, downCents, org, due, date, account))}>
          Planı kur
        </Button>
      </div>
    </div>
  );
}

function RowEditor({ r, problem, accounts, organizations, onOrgCreated, methods, paidNowLabel, onChange, onRemove }: {
  r: PlanRow; problem: string | null; accounts: AccountResponse[]; organizations: ContactResponse[];
  onOrgCreated: (c: ContactResponse) => void; methods: PaymentMethod[];
  paidNowLabel: string; onChange: (patch: Partial<PlanRow>) => void; onRemove: () => void;
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
      <NumberInput className={`${inputClass} h-9 sayi text-[16px]`} value={text} onValueChange={changeAmount} aria-label="Tutar" />
      <select className={`${inputClass} h-9`} value={r.method} aria-label="Yöntem"
        onChange={(e) => onChange({ method: e.target.value as PaymentMethod, payer: null })}>
        {methods.map((m) => <option key={m} value={m}>{METHOD[m]}</option>)}
      </select>
      <div className="col-span-2 md:col-span-1 flex flex-wrap items-center gap-2 min-w-0">
        {r.method === "INSTITUTION" && (
          <div className="w-full md:w-48">
            <ContactPicker kind="ORGANIZATION" className={`${inputClass} h-9`} contacts={organizations} selected={r.payer}
              onSelect={(c) => onChange({ payer: c })} onCreated={onOrgCreated} placeholder="Kurum seç" />
          </div>
        )}
        {r.method === "PROMISSORY_NOTE" && (
          <input className={`${inputClass} h-9 md:w-32`} value={r.noteNo} onChange={(e) => onChange({ noteNo: e.target.value })} placeholder="Senet no" aria-label="Senet no" />
        )}
        <label className="inline-flex items-center gap-1.5 text-[13.5px] font-semibold whitespace-nowrap">
          <input type="checkbox" checked={r.paidNow} className="w-4 h-4 accent-[var(--color-basari)]"
            onChange={(e) => onChange({ paidNow: e.target.checked, accountId: e.target.checked ? (r.accountId || accounts[0]?.id || "") : "" })} />
          {paidNowLabel}
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
