// Odeme plani kurucusu (satis ve alis): hazir seceneklerden taksit satirlari uretir. Tutarlar kurus (tam sayi).
import { addMonthsIso } from "../../shared/format";
import type { ContactSummary, InstallmentRequest, PaymentMethod } from "../../shared/types";

export type PlanMode = "CASH" | "INSTALLMENTS" | "INSTITUTION" | "MIXED";

export interface PlanRow {
  key: number;
  dueDate: string;
  amountCents: number;
  method: PaymentMethod;
  noteNo: string;
  payer: ContactSummary | null;   // null: alici
  paidNow: boolean;
  accountId: string;
}

let nextKey = 1;

export function row(partial: Partial<PlanRow> & Pick<PlanRow, "dueDate" | "amountCents">): PlanRow {
  return { key: nextKey++, method: "CASH", noteNo: "", payer: null, paidNow: false, accountId: "", ...partial };
}

/** Pesin: tek satir, vade satis gunu, "simdi alindi". */
export function cashPlan(priceCents: number, saleDate: string, accountId: string): PlanRow[] {
  return priceCents > 0 ? [row({ dueDate: saleDate, amountCents: priceCents, paidNow: true, accountId })] : [];
}

/**
 * Taksit / senet: pesinat (varsa, simdi alindi) + kalan esit bolunur, ilk vadeden itibaren aylik.
 * Kurus farki son taksite eklenir: 100.000,00 / 3 -> 33.333,33 + 33.333,33 + 33.333,34.
 */
export function installmentPlan(priceCents: number, downCents: number, count: number, firstDue: string,
                                method: PaymentMethod, saleDate: string, accountId: string): PlanRow[] {
  const rows: PlanRow[] = [];
  if (downCents > 0) rows.push(row({ dueDate: saleDate, amountCents: downCents, paidNow: true, accountId }));
  const rest = priceCents - Math.max(0, downCents);
  if (rest <= 0 || count < 1) return rows;
  const base = Math.floor(rest / count);
  for (let k = 0; k < count; k++) {
    const amount = k === count - 1 ? rest - base * (count - 1) : base;
    rows.push(row({ dueDate: addMonthsIso(firstDue, k), amountCents: amount, method }));
  }
  return rows;
}

/** Evim / kredi: pesinat (alicidan, simdi alindi) + kalan kurumdan tek satir. */
export function institutionPlan(priceCents: number, downCents: number, institution: ContactSummary | null,
                                due: string, saleDate: string, accountId: string): PlanRow[] {
  const rows: PlanRow[] = [];
  if (downCents > 0) rows.push(row({ dueDate: saleDate, amountCents: downCents, paidNow: true, accountId }));
  const rest = priceCents - Math.max(0, downCents);
  if (rest > 0) rows.push(row({ dueDate: due, amountCents: rest, method: "INSTITUTION", payer: institution }));
  return rows;
}

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
  };
}
