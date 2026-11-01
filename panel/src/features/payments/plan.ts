// Odeme plani (satis ve alis): yontem secilir, pesinat / adet / ilk vade girilir, satirlar kendiliginden kurulur.
// Satir elle degistirilirse plan "elle" olur ve yeniden kurulana kadar ayarlar satirlari ezmez. Tutarlar kurus (tam sayi).
import { useState } from "react";
import { addMonthsIso, tlToCents } from "../../shared/format";
import type { ContactSummary, InstallmentRequest, PaymentMethod } from "../../shared/types";

/** Odeme sekli: her biri kendi planini kurar. MIXED: satirlar elle. */
export type PlanMode = "CASH" | "PROMISSORY_NOTE" | "CHEQUE" | "OPEN_ACCOUNT" | "INSTITUTION" | "CREDIT_CARD" | "MIXED";

export interface PlanRow {
  key: string;
  dueDate: string;
  amountCents: number;
  method: PaymentMethod;
  noteNo: string;
  bankName: string;
  drawerName: string;
  description: string;
  payer: ContactSummary | null;   // null: alici / satici
  paidNow: boolean;
  accountId: string;
}

/** Plan ayarlari (yontem secilince varsayilanla dolar). */
export interface PlanConfig {
  downText: string;      // pesinat (simdi alinan / odenen)
  accountId: string;     // pesinin / pesinatin girdigi hesap ("" = varsayilan, Kasa)
  count: string;         // taksit / evrak sayisi
  firstDue: string;      // ilk vade
  bankName: string;      // cek
  drawerName: string;    // cek: keside eden
  institution: ContactSummary | null;   // Evim / kredi
  description: string;   // acik hesap
}

let nextKey = 1;

export function row(partial: Partial<PlanRow> & Pick<PlanRow, "dueDate" | "amountCents">): PlanRow {
  return {
    key: `n${nextKey++}`, method: "CASH", noteNo: "", bankName: "", drawerName: "", description: "", payer: null, paidNow: false,
    accountId: "", ...partial,
  };
}

export function defaultConfig(mode: PlanMode, date: string): PlanConfig {
  const later = mode === "INSTITUTION" || mode === "CREDIT_CARD" ? date : addMonthsIso(date, 1);
  return {
    downText: "", accountId: "", count: mode === "PROMISSORY_NOTE" ? "6" : "1", firstDue: later, bankName: "", drawerName: "",
    institution: null, description: "",
  };
}

const METHOD_OF: Record<Exclude<PlanMode, "CASH" | "MIXED">, PaymentMethod> = {
  PROMISSORY_NOTE: "PROMISSORY_NOTE", CHEQUE: "CHEQUE", OPEN_ACCOUNT: "OPEN_ACCOUNT", INSTITUTION: "INSTITUTION", CREDIT_CARD: "CREDIT_CARD",
};

export const centsOrZero = (text: string) => {
  const c = tlToCents(text);
  return c != null && !Number.isNaN(c) ? c : 0;
};

/** Ayar hatasi (plani kurmaya engel) ya da null. */
export function configProblem(mode: PlanMode, cfg: PlanConfig, priceCents: number): string | null {
  if (mode === "CASH" || mode === "MIXED") return null;
  if (priceCents <= 0) return "Önce bedeli gir.";
  const down = tlToCents(cfg.downText);
  if (down != null && Number.isNaN(down)) return "Peşinatı 300.000 gibi yaz.";
  if ((down ?? 0) >= priceCents) return "Peşinat bedelden küçük olmalı.";
  const n = Number(cfg.count);
  if (!Number.isInteger(n) || n < 1 || n > 60) return "Adet 1 ile 60 arasında olmalı.";
  if (!cfg.firstDue) return "İlk vadeyi seç.";
  if (mode === "INSTITUTION" && !cfg.institution) return "Kurumu seç.";
  return null;
}

/**
 * Plan satirlari: pesin tek satir (simdi). Digerlerinde pesinat (varsa, simdi) + kalan adet kadar esit satir, ilk vadeden
 * itibaren aylik; kurus farki son satira (100.000,00 / 3 -> 33.333,33 + 33.333,33 + 33.333,34). Anahtarlar tutar ve vadeyi
 * icerir: degisince satir yeniden cizilir, elle duzenlemede ayni kalir.
 */
export function buildPlan(mode: PlanMode, cfg: PlanConfig, priceCents: number, date: string, defaultAccount: string): PlanRow[] {
  const account = cfg.accountId || defaultAccount;
  if (mode === "MIXED" || priceCents <= 0) return [];
  if (mode === "CASH") {
    return [{ ...row({ dueDate: date, amountCents: priceCents, paidNow: true, accountId: account }), key: `c-${priceCents}-${date}` }];
  }
  if (configProblem(mode, cfg, priceCents)) return [];
  const down = centsOrZero(cfg.downText);
  const count = Number(cfg.count);
  const method = METHOD_OF[mode];
  const rows: PlanRow[] = [];
  if (down > 0) rows.push({ ...row({ dueDate: date, amountCents: down, paidNow: true, accountId: account }), key: `d-${down}-${date}` });
  const rest = priceCents - down;
  const base = Math.floor(rest / count);
  for (let k = 0; k < count; k++) {
    const amount = k === count - 1 ? rest - base * (count - 1) : base;
    const due = addMonthsIso(cfg.firstDue, k);
    rows.push({
      ...row({
        dueDate: due, amountCents: amount, method,
        bankName: mode === "CHEQUE" ? cfg.bankName.trim() : "",
        drawerName: mode === "CHEQUE" ? cfg.drawerName.trim() : "",
        description: mode === "OPEN_ACCOUNT" ? cfg.description.trim() : "",
        payer: mode === "INSTITUTION" ? cfg.institution : null,
      }),
      key: `${mode}-${k}-${amount}-${due}`,
    });
  }
  return rows;
}

/** Plan durumu: secilen yontem, ayarlar ve satirlar (kendiliginden kurulan ya da elle duzenlenen). */
export function usePlan({ priceCents, date, defaultAccount, initialMode = null, initialRows = null }: {
  priceCents: number; date: string; defaultAccount: string; initialMode?: PlanMode | null; initialRows?: PlanRow[] | null;
}) {
  const [mode, setMode] = useState<PlanMode | null>(initialMode);
  const [config, setConfig] = useState<PlanConfig>(() => defaultConfig(initialMode ?? "CASH", date));
  const [manual, setManual] = useState<PlanRow[] | null>(initialRows);
  const generated = mode != null && manual == null ? buildPlan(mode, config, priceCents, date, defaultAccount) : [];
  const rows = manual ?? generated;

  return {
    mode, config, rows,
    /** Satirlar elle degistirildi (yeniden kurulabilir). Karmada her zaman elle. */
    edited: manual != null && mode !== "MIXED",
    problem: mode == null ? null : configProblem(mode, config, priceCents),
    choose(next: PlanMode) {
      setMode(next);
      setConfig((c) => ({ ...defaultConfig(next, date), accountId: c.accountId }));
      if (next === "MIXED") setManual(rows.length ? rows : priceCents > 0 ? [row({ dueDate: date, amountCents: priceCents })] : []);
      else setManual(null);
    },
    update(patch: Partial<PlanConfig>) {
      setConfig((c) => ({ ...c, ...patch }));
      if (mode !== "MIXED") setManual(null);
    },
    setRows(next: PlanRow[]) {
      setManual(next);
    },
    rebuild() {
      setManual(null);
    },
  };
}

export type PlanState = ReturnType<typeof usePlan>;

/** Satir gecerli mi: tutar > 0, kurum satirinda kurum secili, "simdi alindi"da hesap secili. */
export function rowProblem(r: PlanRow): string | null {
  if (!(r.amountCents > 0)) return "Tutar girilmeli";
  if (!r.dueDate) return "Vade girilmeli";
  if (r.method === "INSTITUTION" && !r.payer) return "Kurum seçilmeli";
  if (r.paidNow && !r.accountId) return "Hesap seçilmeli";
  return null;
}

export function toRequest(r: PlanRow): InstallmentRequest {
  return {
    dueDate: r.dueDate,
    amountCents: r.amountCents,
    method: r.method,
    noteNo: r.noteNo.trim() || null,
    payerId: r.payer?.id ?? null,
    paidNow: r.paidNow,
    accountId: r.paidNow ? r.accountId : null,
    bankName: r.method === "CHEQUE" ? r.bankName.trim() || null : null,
    drawerName: r.method === "CHEQUE" ? r.drawerName.trim() || null : null,
    description: r.method === "OPEN_ACCOUNT" ? r.description.trim() || null : null,
  };
}
