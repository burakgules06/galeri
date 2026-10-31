// Ortak aracin sahip satirlari (CoOwnersEditor): durum, dogrulama ve istek. Component dosyasi sadece component export etsin diye ayri.
import { bpToInput, centsToInput, percentToBp, tlToCents } from "../../shared/format";
import type { CommissionType, ContactSummary, ContributionSource, ShareRequest, ShareResponse, VehicleDetailAdmin } from "../../shared/types";

export interface OwnerRow {
  key: number;
  pool: boolean;                    // dukkan sahibinin payi: parasi kasadan (eski Havuz)
  contact: ContactSummary | null;   // sahip kullanici (dukkan sahibi ya da sermayedar)
  percent: string;                  // sahiplik payi
  profitPercent: string;            // kar payi orani (farkliysa)
  source: ContributionSource;
  pocket: string;                   // hibritte cepten tutar (TL)
}

export interface ShopCut { enabled: boolean; type: CommissionType; rate: string; fixed: string }

let ownerKey = 0;
export const ownerRow = (pool: boolean, percent = "", contact: ContactSummary | null = null): OwnerRow =>
  ({ key: ++ownerKey, pool, contact, percent, profitPercent: percent, source: "CAPITAL", pocket: "" });

/** Yeni ortak arac: dukkan sahibi %50 + secilecek sermayedar %50. Duzenlemede kayitli sahipler. */
export function initialOwners(existing?: VehicleDetailAdmin): OwnerRow[] {
  if (!existing?.shares.length) return [ownerRow(true, "50"), ownerRow(false, "50")];
  return existing.shares.map((s: ShareResponse) => ({
    ...ownerRow(s.owner, bpToInput(s.shareBp), { id: s.userId, name: s.name, phone: null }),
    profitPercent: bpToInput(s.profitShareBp), source: s.source, pocket: centsToInput(s.pocketCents),
  }));
}

export function initialShopCut(existing?: VehicleDetailAdmin): ShopCut {
  return {
    enabled: existing?.shopCutType != null, type: existing?.shopCutType ?? "RATE",
    rate: bpToInput(existing?.shopCutRateBp), fixed: centsToInput(existing?.shopCutCents),
  };
}

/** Paylar esit bolunur, son satir kalani alir (toplam %100). */
export function rebalance(rows: OwnerRow[]): OwnerRow[] {
  const base = Math.floor(10000 / rows.length);
  return rows.map((r, k) => {
    const bp = k === rows.length - 1 ? 10000 - base * (rows.length - 1) : base;
    return { ...r, percent: bpToInput(bp), profitPercent: bpToInput(bp) };
  });
}

/** Katki = gercek alis x sahiplik payi; ortaklar asagi yuvarlanir, kurus farki Havuz'a (Havuz yoksa en buyuk paya). */
export function contributions(priceCents: number, bps: number[], poolIndex: number): number[] {
  const parts = bps.map((bp, k) => (k === poolIndex ? 0 : Math.floor((priceCents * bp) / 10000)));
  const rest = priceCents - parts.reduce((t, p) => t + p, 0);
  const target = poolIndex >= 0 ? poolIndex : bps.indexOf(Math.max(...bps));
  if (target >= 0) parts[target] += rest;
  return parts;
}

/** Satış formundaki alacak önizlemesi backend CoOwnershipMath ile aynı kuruş kuralını kullanır. */
export function ownerReceivables(
  profitCents: number, owners: ShareResponse[], shopCutType: CommissionType | null,
  shopCutRateBp: number | null, shopCutCents: number | null,
): Record<string, number> {
  const poolIndex = owners.findIndex((o) => o.owner);
  const shares = owners.map((o) => o.owner ? 0 : Math.floor(profitCents * o.profitShareBp / 10000));
  if (poolIndex >= 0) shares[poolIndex] = profitCents - shares.reduce((sum, part) => sum + part, 0);
  else if (shares.length) {
    // Eşit toplamda partner ağırlıklarıyla böl; son kuruş(lar) en yüksek paya gider.
    const total = owners.reduce((sum, o) => sum + o.profitShareBp, 0);
    let given = 0;
    for (let i = 0; i < owners.length; i++) {
      shares[i] = Math.floor(profitCents * owners[i].profitShareBp / total);
      given += shares[i];
    }
    const largest = owners.reduce((best, o, i) => o.profitShareBp > owners[best].profitShareBp ? i : best, 0);
    shares[largest] += profitCents - given;
  }

  const partners = owners.map((o, i) => o.owner ? -1 : i).filter((i) => i >= 0);
  const partnersProfit = partners.reduce((sum, i) => sum + shares[i], 0);
  const cut = partnersProfit <= 0 || shopCutType == null ? 0
    : shopCutType === "FIXED" ? Math.min(shopCutCents ?? 0, partnersProfit)
      : Math.floor((partnersProfit * (shopCutRateBp ?? 0) + 5000) / 10000);
  if (cut > 0 && partners.length) {
    const total = partners.reduce((sum, i) => sum + owners[i].profitShareBp, 0);
    let given = 0;
    const cuts = partners.map((i) => {
      const part = Math.floor(cut * owners[i].profitShareBp / total);
      given += part;
      return part;
    });
    const largest = partners.reduce((best, i, j) => owners[i].profitShareBp > owners[partners[best]].profitShareBp ? j : best, 0);
    cuts[largest] += cut - given;
    partners.forEach((i, j) => { shares[i] -= cuts[j]; });
    if (poolIndex >= 0) shares[poolIndex] += cut;
  }

  return Object.fromEntries(owners.flatMap((o, i) => o.owner ? [] : [[o.userId, o.contributionCents + shares[i]]]));
}

export const totalBp = (bps: (number | null)[]) => bps.reduce<number>((t, b) => t + (b != null && !Number.isNaN(b) ? b : 0), 0);

/** Kaydetmeden once: hata metni ya da null. */
export function ownersProblem(rows: OwnerRow[], profitDiffers: boolean, priceCents: number, shopCut: ShopCut): string | null {
  const people = rows.filter((r) => !r.pool);
  if (people.length === 0) return "Ortak araçta en az bir sermayedar olmalı.";
  if (people.some((r) => !r.contact)) return "Her ortak satırında sermayedarı seç.";
  if (new Set(people.map((r) => r.contact!.id)).size !== people.length) return "Bir kişi bir kez yazılabilir.";
  const bps = rows.map((r) => percentToBp(r.percent));
  if (bps.some((b) => b == null || Number.isNaN(b) || b <= 0)) return "Sahiplik paylarını 50 ya da 33,34 gibi yaz.";
  if (totalBp(bps) !== 10000) return "Sahiplik payları toplamı %100 olmalı.";
  if (profitDiffers) {
    const profit = rows.map((r) => percentToBp(r.profitPercent));
    if (profit.some((b) => b == null || Number.isNaN(b))) return "Kâr payı oranlarını yaz.";
    if (totalBp(profit) !== 10000) return "Kâr payı oranları toplamı %100 olmalı.";
  }
  const parts = contributions(priceCents, bps as number[], rows.findIndex((r) => r.pool));
  for (let k = 0; k < rows.length; k++) {
    if (rows[k].source !== "HYBRID") continue;
    const pocket = tlToCents(rows[k].pocket);
    if (pocket == null || Number.isNaN(pocket) || pocket <= 0) return "Hibrit katkıda cepten tutarı yaz.";
    if (pocket > parts[k]) return "Cepten tutar ortağın katkısından büyük olamaz.";
  }
  if (shopCut.enabled) {
    const value = shopCut.type === "RATE" ? percentToBp(shopCut.rate) : tlToCents(shopCut.fixed);
    if (value == null || Number.isNaN(value)) return shopCut.type === "RATE" ? "Dükkân payı oranını 10 gibi yaz." : "Dükkân payını 25.000 gibi yaz.";
  }
  return null;
}

export function toShareRequests(rows: OwnerRow[], profitDiffers: boolean, ownerId: string): ShareRequest[] {
  return rows.map((r) => ({
    userId: r.pool ? ownerId : r.contact!.id,
    shareBp: percentToBp(r.percent)!,
    profitShareBp: profitDiffers ? percentToBp(r.profitPercent) : null,
    source: r.pool ? null : r.source,
    pocketCents: !r.pool && r.source === "HYBRID" ? tlToCents(r.pocket) : null,
  }));
}
