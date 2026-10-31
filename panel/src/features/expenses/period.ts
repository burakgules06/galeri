// Masraf ekranlarinda donem: bir ay ya da tum zamanlar. ISO tarih (YYYY-MM-DD).
import { MONTHS } from "../../shared/format";
import type { LedgerEntryResponse } from "../../shared/types";

/** Gecerli kayit: duzeltme degil, duzeltilmemis. */
export const isActiveEntry = (e: LedgerEntryResponse) => !e.corrected && e.correctedEntryId == null;

export interface Period { year: number; month: number | null }   // month: 0-11, null = tum zamanlar

export function currentPeriod(today: string): Period {
  return { year: Number(today.slice(0, 4)), month: Number(today.slice(5, 7)) - 1 };
}

export function periodRange(p: Period): { from: string | null; to: string | null } {
  if (p.month == null) return { from: null, to: null };
  const mm = String(p.month + 1).padStart(2, "0");
  const last = new Date(Date.UTC(p.year, p.month + 1, 0)).getUTCDate();
  return { from: `${p.year}-${mm}-01`, to: `${p.year}-${mm}-${String(last).padStart(2, "0")}` };
}

export function shiftPeriod(p: Period, by: number): Period {
  if (p.month == null) return p;
  const m = p.month + by;
  return { year: p.year + Math.floor(m / 12), month: ((m % 12) + 12) % 12 };
}

export function periodLabel(p: Period): string {
  return p.month == null ? "Tüm zamanlar" : `${MONTHS[p.month]} ${p.year}`;
}
