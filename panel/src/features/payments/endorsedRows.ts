// Arac alisinda elden verilen (portfoydeki) cek / senet satirlari: her biri ayni tutarda bir alis taksiti olur ve kayitta
// hemen satıcıya ciro edilir. Plan bu tutarlar dusuldukten sonra kalan icin kurulur.
import type { InstallmentRequest, InstallmentResponse } from "../../shared/types";

export interface EndorsedRow {
  instrument: InstallmentResponse;
  description: string;
}

export const endorsedTotal = (rows: EndorsedRow[]) => rows.reduce((t, r) => t + r.instrument.amountCents, 0);

/** Portfoyde, ciro edilebilir evrak: alinmis, odenmemis cek / senet; saticinin kendi verdigi evrak geri verilemez. */
export function endorsable(list: InstallmentResponse[] | null, sellerId: string | null, picked: EndorsedRow[]): InstallmentResponse[] {
  return (list ?? []).filter((p) => p.direction === "SALE" && p.status !== "PAID"
    && (p.method === "CHEQUE" || p.method === "PROMISSORY_NOTE")
    && p.payer.id !== sellerId && !picked.some((r) => r.instrument.id === p.id))
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate));
}

export function endorsedRequest(r: EndorsedRow, date: string): InstallmentRequest {
  return {
    dueDate: date,
    amountCents: r.instrument.amountCents,
    method: r.instrument.method,
    noteNo: null,
    payerId: null,
    paidNow: false,
    accountId: null,
    description: r.description.trim() || null,
    endorseInstallmentId: r.instrument.id,
  };
}
