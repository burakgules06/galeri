import { bpToInput, CONTRIBUTION_SOURCE, percentToBp, tl, tlToCents } from "../../shared/format";
import type { ContactResponse, ContactSummary, ContributionSource, Partner } from "../../shared/types";
import { Button, inputClass } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";
import { ContactPicker } from "../contacts/ContactPicker";
import { contributions, ownerRow, rebalance, totalBp, type OwnerRow } from "./coOwners";

interface Props {
  rows: OwnerRow[];
  onRows: (rows: OwnerRow[]) => void;
  profitDiffers: boolean;
  onProfitDiffers: (v: boolean) => void;
  priceCents: number;
  contacts: ContactResponse[];
  onContactCreated: (c: ContactResponse) => void;
  partners: Partner[];
  boundHere: Record<string, number>;
}

export function CoOwnersEditor({ rows, onRows, profitDiffers, onProfitDiffers, priceCents,
  contacts, onContactCreated, partners, boundHere }: Props) {
  const bps = rows.map(r => percentToBp(r.percent));
  const total = totalBp(bps);
  const profitTotal = totalBp(rows.map(r => percentToBp(r.profitPercent)));
  const parts = contributions(priceCents, bps.map(b => b != null && !Number.isNaN(b) ? b : 0), rows.findIndex(r => r.pool));
  const update = (key: number, change: Partial<OwnerRow>) => onRows(rows.map(x => x.key === key ? { ...x, ...change } : x));
  const partnerOf = (id: string | undefined) => partners.find(p => p.contactId === id);
  const selectContact = (r: OwnerRow, c: ContactSummary | null) => {
    const source = c && !partnerOf(c.id) && r.source === "CAPITAL" ? "POCKET" : r.source;
    update(r.key, { contact: c, source });
  };
  return <div className="space-y-3">
    {rows.map((r, k) => {
      const partner = partnerOf(r.contact?.id);
      const typed = tlToCents(r.pocket);
      const hybrid = typed != null && !Number.isNaN(typed) && typed > 0 ? Math.min(parts[k], typed) : 0;
      const pocket = r.source === "POCKET" ? parts[k] : r.source === "HYBRID" ? hybrid : 0;
      const fromCapital = r.pool ? 0 : parts[k] - pocket;
      const available = partner ? partner.cashCents + (boundHere[partner.contactId] ?? 0) : 0;
      return <div key={r.key} className={`border-l-[3px] pl-3 py-1 space-y-2 ${r.pool ? "border-plaka" : "border-ortakli"}`}>
        <div className="grid grid-cols-[minmax(0,1fr)_80px_40px] gap-2 items-start">
          {r.pool ? <span className="h-10 flex items-center font-semibold">Havuz</span>
            : <ContactPicker contacts={contacts} selected={r.contact} onSelect={c => selectContact(r, c)} onCreated={onContactCreated} placeholder="Ortak" />}
          <PercentInput value={r.percent} label="Sahiplik payı" onChange={percent => update(r.key, profitDiffers ? { percent } : { percent, profitPercent: percent })} />
          <button type="button" aria-label="Sahibi kaldır" disabled={rows.length <= 1}
            onClick={() => onRows(rebalance(rows.filter(x => x.key !== r.key)))}
            className="basilir h-10 w-10 rounded-lg text-murekkep-3 hover:bg-yuzey-2 hover:text-tehlike disabled:opacity-30">×</button>
        </div>
        {profitDiffers && <div className="flex items-center justify-between gap-3 text-sm">
          <span>Kâr payı</span><div className="w-24"><PercentInput value={r.profitPercent} label="Kâr payı oranı" onChange={profitPercent => update(r.key, { profitPercent })} /></div>
        </div>}
        {!r.pool && <div className="flex flex-wrap items-start gap-2">
          <select className={`${inputClass} w-auto text-sm`} aria-label="Katkı kaynağı" value={r.source}
            onChange={e => update(r.key, { source: e.target.value as ContributionSource })}>
            {(Object.keys(CONTRIBUTION_SOURCE) as ContributionSource[]).map(s => <option key={s} value={s}>{CONTRIBUTION_SOURCE[s]}</option>)}
          </select>
          {r.source === "HYBRID" && <NumberInput className={`${inputClass} w-36`} value={r.pocket} onValueChange={pocket => update(r.key, { pocket })}
            placeholder="Cepten TL" aria-label="Cepten tutar" />}
        </div>}
        <p className="text-sm">Katkı <span className="sayi text-lg">{tl(parts[k])}</span></p>
        {!r.pool && r.contact && fromCapital > available && <p className="text-sm text-tehlike">{tl(fromCapital - available)} cepten eklenmeli</p>}
      </div>;
    })}
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div className="flex gap-1">
        <Button type="button" variant="quiet" className="px-2 text-plaka" onClick={() => onRows(rebalance([...rows, ownerRow(false)]))}>+ Ortak ekle</Button>
        {!rows.some(r => r.pool) && <Button type="button" variant="quiet" className="px-2 text-plaka" onClick={() => onRows(rebalance([ownerRow(true), ...rows]))}>+ Havuz ekle</Button>}
      </div>
      <span className={`text-sm font-semibold ${total === 10000 ? "text-basari" : "text-uyari"}`}>
        Sahiplik %{bpToInput(total) || "0"}{total === 10000 ? " ✓" : " · %100 olmalı"}
        {profitDiffers && <> · Kâr payı %{bpToInput(profitTotal) || "0"}{profitTotal === 10000 ? " ✓" : ""}</>}
      </span>
    </div>
    <label className="flex items-center gap-2 text-sm cursor-pointer min-h-10">
      <input type="checkbox" className="w-4 h-4 accent-plaka" checked={profitDiffers} onChange={e => {
        onProfitDiffers(e.target.checked);
        if (!e.target.checked) onRows(rows.map(r => ({ ...r, profitPercent: r.percent })));
      }} />Kâr payı farklı
    </label>
  </div>;
}

function PercentInput({ value, label, onChange }: { value: string; label: string; onChange: (v: string) => void }) {
  return <div className="relative">
    <NumberInput className={`${inputClass} pr-7 text-right`} value={value} placeholder="50" aria-label={label} preview={false} onValueChange={onChange} />
    <span className="absolute right-2.5 top-2 text-murekkep-3">%</span>
  </div>;
}
