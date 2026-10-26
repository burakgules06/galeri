// Satis formundaki ortaga donus taslagi ve istege cevirme (OwnerReturns.tsx sadece component export etsin diye ayri; ad harf buyuklugunden farkli).
import { tlToCents } from "../../shared/format";
import type { OwnerReturnMode, OwnerReturnRequest } from "../../shared/types";

export interface OwnerReturnDraft { mode: OwnerReturnMode; amount: string; accountId: string }

/** Sermayede kalanlar gonderilmez. Hata varsa metin, yoksa istek listesi. */
export function toOwnerReturns(drafts: Record<string, OwnerReturnDraft>, receivables: Record<string, number>): OwnerReturnRequest[] | string {
  const result: OwnerReturnRequest[] = [];
  for (const [contactId, d] of Object.entries(drafts)) {
    if (d.mode === "KEEP") continue;
    if (!d.accountId) return "Ortağa ödeme için kasa ya da banka seç.";
    const maximum = receivables[contactId];
    if (maximum == null) return "Ortak alacağı hesaplanamadı; maliyet bilgisi yüklenince yeniden dene.";
    if (maximum <= 0) return "Ortağın pozitif alacağı yok; nakit dönüş yapılamaz.";
    const amount = d.mode === "HYBRID" ? tlToCents(d.amount) : null;
    if (d.mode === "HYBRID" && (amount == null || Number.isNaN(amount) || amount <= 0)) return "Ortağa nakit ödenecek tutarı yaz.";
    if (d.mode === "HYBRID" && amount! > maximum) return "Nakit dönüş ortağın alacağından büyük olamaz.";
    result.push({ contactId, mode: d.mode, amountCents: amount, accountId: d.accountId });
  }
  return result;
}
