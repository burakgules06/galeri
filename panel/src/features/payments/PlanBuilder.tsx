// Odeme plani kurucusu: once odeme sekli (Nakit, Senet, Cek, Acik hesap, Evim / kredi, Kredi karti, Karma) secilir, sonra
// pesinat / adet / ilk vade girilir; satirlar yazarken kurulur ve asagida gorunur. Satirlar elle de duzenlenebilir.
// Satis formu ve arac formundaki planli alis ayni bileseni kullanir; fark etiketler ve izin verilen sekiller.
import { useState } from "react";
import { centsToInput, METHOD, tl, tlToCents } from "../../shared/format";
import type { AccountResponse, ContactResponse, PaymentMethod } from "../../shared/types";
import { ContactPicker } from "../contacts/ContactPicker";
import { Button, Field, inputClass } from "../../shared/ui/Common";
import { centsOrZero, row, rowProblem, type PlanMode, type PlanRow, type PlanState } from "./plan";
import { NumberInput } from "../../shared/ui/NumberInput";

const MODE: Record<PlanMode, { label: string; hint: string }> = {
  CASH: { label: "Nakit", hint: "Tek seferde" },
  PROMISSORY_NOTE: { label: "Senet", hint: "Vadeli senetler" },
  CHEQUE: { label: "Çek", hint: "Vadeli çekler" },
  OPEN_ACCOUNT: { label: "Açık hesap", hint: "Senetsiz vade" },
  INSTITUTION: { label: "Evim / kredi", hint: "Kurum öder" },
  CREDIT_CARD: { label: "Kredi kartı", hint: "Tek çekim / taksit" },
  MIXED: { label: "Karma", hint: "Satırları sen kur" },
};

const COUNT_LABEL: Partial<Record<PlanMode, string>> = {
  PROMISSORY_NOTE: "Senet sayısı", CHEQUE: "Çek sayısı", OPEN_ACCOUNT: "Taksit sayısı", INSTITUTION: "Ödeme sayısı", CREDIT_CARD: "Taksit sayısı",
};

export function PlanBuilder({
  plan, baseCents, accounts, organizations, onOrgCreated = () => {}, modes, methods, nowLabel, downLabel,
}: {
  plan: PlanState;
  baseCents: number;                 // plana dagitilacak tutar (bedel - odenmis taksitler)
  accounts: AccountResponse[];
  organizations: ContactResponse[];
  onOrgCreated?: (c: ContactResponse) => void;
  modes: PlanMode[];
  methods: PaymentMethod[];          // satirlarda secilebilecek yontemler
  nowLabel: string;                  // "Şimdi alındı" / "Şimdi ödendi"
  downLabel: string;                 // "Peşinat (şimdi alınır)" / "Peşinat (şimdi ödenir)"
}) {
  const { mode, config: cfg, rows } = plan;
  const defaultAccount = (accounts.find((a) => a.type === "CASH") ?? accounts[0])?.id ?? "";
  const problems = rows.map((r) => rowProblem(r));
  const generated = mode != null && mode !== "CASH" && mode !== "MIXED";
  const down = centsOrZero(cfg.downText);

  return (
    <div className="space-y-4">
      <div role="radiogroup" aria-label="Ödeme şekli" className="grid grid-cols-2 min-[420px]:grid-cols-3 md:grid-cols-4 gap-2">
        {modes.map((m) => {
          const on = mode === m;
          return (
            <button key={m} type="button" role="radio" aria-checked={on} onClick={() => plan.choose(m)}
              className={`basilir text-left rounded-[12px] border px-3 py-2.5 transition-colors ${
                on ? "border-plaka bg-plaka-acik ring-1 ring-plaka" : "border-cizgi-2 bg-yuzey hover:bg-yuzey-2"}`}>
              <span className={`block font-semibold text-[15px] leading-tight ${on ? "text-plaka-koyu" : ""}`}>{MODE[m].label}</span>
              <span className="block text-[12px] text-murekkep-2 mt-0.5">{MODE[m].hint}</span>
            </button>
          );
        })}
      </div>

      {mode === "CASH" && (
        <div className="rounded-[12px] bg-yuzey-2 border border-cizgi p-3 grid gap-3 sm:grid-cols-2">
          <Field label="Para nereye">
            <select className={inputClass} value={cfg.accountId || defaultAccount} onChange={(e) => plan.update({ accountId: e.target.value })}>
              {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          </Field>
          <div className="self-end text-[14px] text-murekkep-2 pb-2">Tamamı <b className="sayi text-[17px] text-murekkep">{tl(baseCents)}</b> şimdi</div>
        </div>
      )}

      {generated && (
        <div className="rounded-[12px] bg-yuzey-2 border border-cizgi p-3 space-y-3">
          <div className="grid gap-3 grid-cols-2 md:grid-cols-4">
            <Field label={downLabel}><NumberInput value={cfg.downText} onValueChange={(downText) => plan.update({ downText })} placeholder="0" /></Field>
            <Field label="Peşinat nereye">
              <select className={inputClass} value={cfg.accountId || defaultAccount} disabled={down <= 0}
                onChange={(e) => plan.update({ accountId: e.target.value })}>
                {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
            </Field>
            <Field label={COUNT_LABEL[mode] ?? "Adet"}>
              <NumberInput kind="integer" value={cfg.count} onValueChange={(count) => plan.update({ count })} />
            </Field>
            <Field label={mode === "INSTITUTION" ? "Kurumun ödeme günü" : "İlk vade"}>
              <input type="date" className={inputClass} value={cfg.firstDue} onChange={(e) => plan.update({ firstDue: e.target.value })} />
            </Field>
            {mode === "CHEQUE" && <>
              <Field label="Banka"><input className={inputClass} value={cfg.bankName} onChange={(e) => plan.update({ bankName: e.target.value })} /></Field>
              <Field label="Keşideci"><input className={inputClass} value={cfg.drawerName} onChange={(e) => plan.update({ drawerName: e.target.value })} /></Field>
            </>}
            {mode === "INSTITUTION" && (
              <div className="col-span-2">
                <Field label="Kurum">
                  <ContactPicker kind="ORGANIZATION" contacts={organizations} selected={cfg.institution}
                    onSelect={(institution) => plan.update({ institution })} onCreated={onOrgCreated} placeholder="Evim şirketi, banka…" />
                </Field>
              </div>
            )}
            {mode === "OPEN_ACCOUNT" && (
              <div className="col-span-2">
                <Field label="Açıklama"><input className={inputClass} value={cfg.description} placeholder="İsteğe bağlı"
                  onChange={(e) => plan.update({ description: e.target.value })} /></Field>
              </div>
            )}
          </div>
          <p className={`text-[13px] min-h-5 ${plan.problem ? "text-uyari font-semibold" : "text-murekkep-2"}`}>
            {plan.problem ?? <>Kalan {tl(baseCents - down)} → {cfg.count} × ~{tl(Math.floor((baseCents - down) / Math.max(1, Number(cfg.count) || 1)))}, aylık</>}
          </p>
        </div>
      )}

      {rows.length > 0 && (
        <div className="space-y-2">
          <div className="flex items-center justify-between min-h-8">
            <p className="etiket">Plan · {rows.length} satır</p>
            {plan.edited && (
              <span className="flex items-center gap-2 text-[13px] text-murekkep-2">
                Elle düzenlendi
                <Button type="button" variant="quiet" className="h-8 px-2 text-plaka" onClick={plan.rebuild}>Yeniden kur</Button>
              </span>
            )}
          </div>
          <div className="hidden md:grid grid-cols-[140px_150px_140px_1fr_40px] gap-2 px-1 etiket">
            <span>Vade</span><span>Tutar</span><span>Yöntem</span><span>Ayrıntı</span><span />
          </div>
          {rows.map((r, k) => (
            <RowEditor key={r.key} r={r} problem={problems[k]} accounts={accounts} organizations={organizations} onOrgCreated={onOrgCreated}
              methods={methods} nowLabel={nowLabel}
              onChange={(patch) => plan.setRows(rows.map((x) => (x.key === r.key ? { ...x, ...patch } : x)))}
              onRemove={() => plan.setRows(rows.filter((x) => x.key !== r.key))} />
          ))}
        </div>
      )}
      {mode != null && mode !== "CASH" && (
        <Button type="button" variant="secondary" disabled={baseCents <= 0}
          onClick={() => {
            const planned = rows.reduce((t, r) => t + (r.amountCents > 0 ? r.amountCents : 0), 0);
            plan.setRows([...rows, row({ dueDate: rows.at(-1)?.dueDate ?? cfg.firstDue, amountCents: Math.max(0, baseCents - planned) })]);
          }}>
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

function RowEditor({ r, problem, accounts, organizations, onOrgCreated, methods, nowLabel, onChange, onRemove }: {
  r: PlanRow; problem: string | null; accounts: AccountResponse[]; organizations: ContactResponse[];
  onOrgCreated: (c: ContactResponse) => void; methods: PaymentMethod[];
  nowLabel: string; onChange: (patch: Partial<PlanRow>) => void; onRemove: () => void;
}) {
  const [text, setText] = useState(centsToInput(r.amountCents));
  const changeAmount = (t: string) => {
    setText(t);
    const cents = tlToCents(t);
    onChange({ amountCents: cents != null && !Number.isNaN(cents) ? cents : 0 });
  };
  const options = methods.includes(r.method) ? methods : [r.method, ...methods];

  return (
    <div className={`rounded-[12px] border p-2.5 md:p-2 grid gap-2 grid-cols-2 md:grid-cols-[140px_150px_140px_1fr_40px] md:items-center ${
      problem ? "border-uyari bg-uyari-zemin/40" : r.paidNow ? "border-basari/30 bg-basari-zemin/50" : "border-cizgi bg-yuzey"}`}>
      <input type="date" className={`${inputClass} h-9`} value={r.dueDate} onChange={(e) => onChange({ dueDate: e.target.value })} aria-label="Vade" />
      <NumberInput className={`${inputClass} h-9 sayi text-[16px]`} value={text} onValueChange={changeAmount} aria-label="Tutar" preview={false} />
      <select className={`${inputClass} h-9`} value={r.method} aria-label="Yöntem"
        onChange={(e) => onChange({ method: e.target.value as PaymentMethod, payer: null })}>
        {options.map((m) => <option key={m} value={m}>{METHOD[m]}</option>)}
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
        {r.method === "OPEN_ACCOUNT" && (
          <input className={`${inputClass} h-9 md:w-48`} value={r.description} onChange={(e) => onChange({ description: e.target.value })} placeholder="Açıklama" aria-label="Açıklama" />
        )}
        {r.method === "CHEQUE" && (
          <div className="w-full grid grid-cols-3 gap-2">
            <input className={`${inputClass} h-9`} value={r.noteNo} onChange={(e) => onChange({ noteNo: e.target.value })} placeholder="Çek no" aria-label="Çek no" />
            <input className={`${inputClass} h-9`} value={r.bankName} onChange={(e) => onChange({ bankName: e.target.value })} placeholder="Banka" aria-label="Banka" />
            <input className={`${inputClass} h-9`} value={r.drawerName} onChange={(e) => onChange({ drawerName: e.target.value })} placeholder="Keşideci" aria-label="Keşideci" />
          </div>
        )}
        <label className="inline-flex items-center gap-1.5 text-[13.5px] font-semibold whitespace-nowrap">
          <input type="checkbox" checked={r.paidNow} className="w-4 h-4 accent-[var(--color-basari)]"
            onChange={(e) => onChange({ paidNow: e.target.checked, accountId: e.target.checked ? (r.accountId || accounts.find((a) => a.type === "CASH")?.id || accounts[0]?.id || "") : "" })} />
          {nowLabel}
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
