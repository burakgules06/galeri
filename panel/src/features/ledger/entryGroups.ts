// Kasa son hareketler: ayni gun, ayni turde, ayni araca ya da ayni kisiye, ayni kasa / bankadan yapilan
// birden fazla hareket tek satirda toplanir (ornek: bir araca girilen toplu masraf). Duzeltmeler gruplanmaz.
import type { LedgerEntryResponse } from "../../shared/types";

const MONEY = new Set(["CASH", "BANK"]);

function groupKey(e: LedgerEntryResponse): string | null {
  if (e.corrected || e.correctedEntryId != null) return null;
  const money = MONEY.has(e.source.type) ? e.source : MONEY.has(e.target.type) ? e.target : null;
  const other = money === e.source ? e.target : e.source;
  // Arac yoksa kisi: kasa / bankanin karsisindaki cari ya da sermaye hesabi
  const subject = e.vehicle?.id ?? (other.type === "BALANCE" || other.type === "CAPITAL" ? other.id : null);
  if (!subject) return null;
  return [e.date, e.type, subject, money?.id ?? "-"].join("|");
}

/** Sirayi koruyarak gruplar: her grup ilk elemaninin yerinde durur. */
export function groupEntries(entries: LedgerEntryResponse[]): LedgerEntryResponse[][] {
  const groups: LedgerEntryResponse[][] = [];
  const byKey = new Map<string, LedgerEntryResponse[]>();
  for (const e of entries) {
    const key = groupKey(e);
    const existing = key ? byKey.get(key) : undefined;
    if (existing) { existing.push(e); continue; }
    const g = [e];
    groups.push(g);
    if (key) byKey.set(key, g);
  }
  return groups;
}

/**
 * Grubun ekranda gosterilen toplami: giris (+) ve cikislar (-) yonune gore netlesir
 * (5.000 giris, 3.015 ve 145.000 cikis -> -143.015). Hepsi notr ise duz toplam.
 */
export function groupTotal(entries: LedgerEntryResponse[], direction: (e: LedgerEntryResponse) => number): { cents: number; dir: number; mixed: boolean } {
  const dirs = entries.map(direction);
  if (dirs.every((d) => d === 0)) return { cents: entries.reduce((t, x) => t + x.amountCents, 0), dir: 0, mixed: false };
  const net = entries.reduce((t, x, k) => t + Math.sign(dirs[k]) * x.amountCents, 0);
  const signs = new Set(dirs.filter((d) => d !== 0).map(Math.sign));
  return { cents: Math.abs(net), dir: Math.sign(net), mixed: signs.size > 1 };
}
