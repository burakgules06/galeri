// Ortak arac sahipleri: Havuz (galeri kasasi) ve/veya ortaklar. Her satirda sahiplik payi; ortakta katki kaynagi
// (sermayeden / cepten / hibrit) ve nakitteki sermayesi. Istege bagli: kar payi orani farkli, dukkan payi.
import { bpToInput, CONTRIBUTION_SOURCE, percentToBp, tl, tlToCents } from "../../shared/format";
import type { CommissionType, ContactResponse, ContactSummary, ContributionSource, Partner } from "../../shared/types";
import { Button, Field, inputClass, Segment } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";
import { ContactPicker } from "../contacts/ContactPicker";
import { contributions, ownerRow, rebalance, totalBp, type OwnerRow, type ShopCut } from "./coOwners";

interface Props {
  rows: OwnerRow[];
  onRows: (rows: OwnerRow[]) => void;
  profitDiffers: boolean;
  onProfitDiffers: (v: boolean) => void;
  shopCut: ShopCut;
  onShopCut: (s: ShopCut) => void;
  priceCents: number;
  contacts: ContactResponse[];
  onContactCreated: (c: ContactResponse) => void;
  partners: Partner[];
  /** Duzenlemede ortagin bu araca zaten bagli katkisi: nakitteki sermayesine eklenerek gosterilir */
  boundHere: Record<string, number>;
}

export function CoOwnersEditor({
  rows, onRows, profitDiffers, onProfitDiffers, shopCut, onShopCut, priceCents, contacts, onContactCreated, partners, boundHere,
}: Props) {
  const bps = rows.map((r) => percentToBp(r.percent));
  const total = totalBp(bps);
  const profitTotal = totalBp(rows.map((r) => percentToBp(r.profitPercent)));
  const parts = contributions(priceCents, bps.map((b) => (b != null && !Number.isNaN(b) ? b : 0)), rows.findIndex((r) => r.pool));
  const update = (key: number, change: Partial<OwnerRow>) => onRows(rows.map((x) => (x.key === key ? { ...x, ...change } : x)));
  const partnerOf = (id: string | undefined) => partners.find((p) => p.contactId === id);
  const hasPool = rows.some((r) => r.pool);

  const selectContact = (r: OwnerRow, c: ContactSummary | null) => {
    // Ortak olmayan kisinin sermayesi yok: katkisi cepten (kaydedince ortak yapilir)
    const source: ContributionSource = c && !partnerOf(c.id) && r.source === "CAPITAL" ? "POCKET" : r.source;
    update(r.key, { contact: c, source });
  };

  return (
    <div className="space-y-3">
      <p className="text-[13px] text-murekkep-2">
        Katkı = gerçek alış × sahiplik payı; masraflar katkıya eklenmez, kârdan düşer. Satışta ortağın kâr payı sermayesine yazılır,
        Havuz'un payı dönem kârına girer.
      </p>

      {rows.map((r, k) => {
        const partner = partnerOf(r.contact?.id);
        const typed = tlToCents(r.pocket);
        const hybrid = typed != null && !Number.isNaN(typed) && typed > 0 ? Math.min(parts[k], typed) : 0;
        const pocket = r.source === "POCKET" ? parts[k] : r.source === "HYBRID" ? hybrid : 0;
        const fromCapital = r.pool ? 0 : parts[k] - pocket;
        const available = partner ? partner.cashCents + (boundHere[partner.contactId] ?? 0) : 0;
        return (
          <div key={r.key} className={`border-l-[3px] pl-3 py-1 space-y-2 ${r.pool ? "border-plaka" : "border-ortakli"}`}>
            <div className={`grid gap-2 items-start ${profitDiffers ? "grid-cols-[1fr_84px_84px_40px]" : "grid-cols-[1fr_92px_40px]"}`}>
              {r.pool
                ? <div className="h-10 flex items-center gap-2"><span className="font-semibold">Havuz</span><span className="text-[13px] text-murekkep-3">galeri kasası</span></div>
                : <ContactPicker contacts={contacts} selected={r.contact} onSelect={(c) => selectContact(r, c)} onCreated={onContactCreated} placeholder="Ortak" />}
              <PercentInput value={r.percent} label="Sahiplik payı" onChange={(percent) => update(r.key, profitDiffers ? { percent } : { percent, profitPercent: percent })} />
              {profitDiffers && <PercentInput value={r.profitPercent} label="Kâr payı oranı" onChange={(profitPercent) => update(r.key, { profitPercent })} />}
              <button type="button" aria-label="Sahibi kaldır" disabled={rows.length <= 1}
                onClick={() => onRows(rebalance(rows.filter((x) => x.key !== r.key)))}
                className="basilir h-10 w-10 rounded-lg grid place-items-center text-murekkep-3 hover:bg-yuzey-2 hover:text-tehlike disabled:opacity-30">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
              </button>
            </div>
            {r.pool
              ? <p className="text-[13px] text-murekkep-2">Katkı <b className="sayi text-[15px] text-murekkep">{tl(parts[k])}</b> · kasadan</p>
              : <>
                  <div className="flex flex-wrap items-center gap-2">
                    <Segment<ContributionSource> value={r.source} onChange={(source) => update(r.key, { source })}
                      options={(Object.keys(CONTRIBUTION_SOURCE) as ContributionSource[]).map((s) => ({ value: s, label: CONTRIBUTION_SOURCE[s] }))} />
                    {r.source === "HYBRID" && (
                      <NumberInput className={`${inputClass} w-40`} value={r.pocket} onValueChange={(pocket) => update(r.key, { pocket })}
                        placeholder="Cepten TL" aria-label="Cepten tutar" />
                    )}
                  </div>
                  <p className="text-[13px] text-murekkep-2">
                    Katkı <b className="sayi text-[15px] text-murekkep">{tl(parts[k])}</b>
                    {pocket > 0 && <> · cepten <b className="sayi text-[15px] text-murekkep">{tl(pocket)}</b> (otomatik sermaye girişi)</>}
                    {r.contact && (partner
                      ? <> · nakitteki sermayesi <b className={`sayi text-[15px] ${fromCapital > available ? "text-tehlike" : "text-murekkep"}`}>{tl(available)}</b>
                          {fromCapital > available && <span className="text-tehlike"> · {tl(fromCapital - available)} cepten eklenmeli</span>}</>
                      : <span className="text-uyari"> · henüz ortak değil, kaydedince ortak yapılır</span>)}
                  </p>
                </>}
          </div>
        );
      })}

      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex gap-1">
          <Button type="button" variant="quiet" className="h-9 px-2 text-plaka" onClick={() => onRows(rebalance([...rows, ownerRow(false)]))}>+ Ortak ekle</Button>
          {!hasPool && <Button type="button" variant="quiet" className="h-9 px-2 text-plaka" onClick={() => onRows(rebalance([ownerRow(true), ...rows]))}>+ Havuz ekle</Button>}
        </div>
        <span className={`text-[14px] font-semibold ${total === 10000 ? "text-basari" : "text-uyari"}`}>
          Sahiplik %{bpToInput(total) || "0"}{total === 10000 ? " ✓" : " · %100 olmalı"}
          {profitDiffers && <> · Kâr payı %{bpToInput(profitTotal) || "0"}{profitTotal === 10000 ? " ✓" : ""}</>}
        </span>
      </div>

      <label className="flex items-center gap-2 text-[14px] cursor-pointer">
        <input type="checkbox" className="w-4 h-4 accent-plaka" checked={profitDiffers}
          onChange={(e) => {
            onProfitDiffers(e.target.checked);
            if (!e.target.checked) onRows(rows.map((r) => ({ ...r, profitPercent: r.percent })));
          }} />
        Kâr payı oranı sahiplik payından farklı
      </label>
      <label className="flex items-center gap-2 text-[14px] cursor-pointer">
        <input type="checkbox" className="w-4 h-4 accent-plaka" checked={shopCut.enabled} onChange={(e) => onShopCut({ ...shopCut, enabled: e.target.checked })} />
        Dükkân payı: ortakların kârından Havuz'a kalsın
      </label>
      {shopCut.enabled && (
        <div className="grid grid-cols-[auto_1fr] gap-3 items-start">
          <Field label="Dükkân payı">
            <Segment<CommissionType> value={shopCut.type} onChange={(type) => onShopCut({ ...shopCut, type })}
              options={[{ value: "RATE", label: "Oran %" }, { value: "FIXED", label: "Sabit TL" }]} />
          </Field>
          {shopCut.type === "RATE"
            ? <Field label="Oran" hint="Ortakların kâr payının yüzdesi">
                <NumberInput value={shopCut.rate} onValueChange={(rate) => onShopCut({ ...shopCut, rate })} placeholder="10" preview={false} />
              </Field>
            : <Field label="Tutar" hint="Kârdan, en fazla ortakların kâr payı kadar">
                <NumberInput value={shopCut.fixed} onValueChange={(fixed) => onShopCut({ ...shopCut, fixed })} placeholder="25.000" />
              </Field>}
        </div>
      )}
    </div>
  );
}

function PercentInput({ value, label, onChange }: { value: string; label: string; onChange: (v: string) => void }) {
  return (
    <div className="relative">
      <NumberInput className={`${inputClass} pr-7 text-right`} value={value} placeholder="50" aria-label={label} preview={false} onValueChange={onChange} />
      <span className="absolute right-2.5 top-2 text-murekkep-3">%</span>
    </div>
  );
}
