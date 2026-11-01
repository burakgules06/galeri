// Masraflarin kategoriye gore toplami, buyukten kucuge yatay cubuklar.
import { tl } from "../../shared/format";
import type { LedgerEntryResponse } from "../../shared/types";

export function CategoryBars({ entries }: { entries: LedgerEntryResponse[] }) {
  const totals = new Map<string, number>();
  for (const e of entries) totals.set(e.category ?? "Diğer", (totals.get(e.category ?? "Diğer") ?? 0) + e.amountCents);
  const rows = [...totals].sort((a, b) => b[1] - a[1]);
  const max = rows[0]?.[1] ?? 0;
  if (rows.length === 0) return <p className="text-[14px] text-murekkep-3">Kayıt yok.</p>;
  return (
    <ul className="space-y-2.5">
      {rows.map(([name, cents]) => (
        <li key={name}>
          <div className="flex items-baseline justify-between gap-3 text-[14px]">
            <span className="font-semibold truncate">{name}</span>
            <span className="sayi whitespace-nowrap">{tl(cents)}</span>
          </div>
          <div className="mt-1 h-2 rounded-full bg-yuzey-2 overflow-hidden">
            <div className="h-full rounded-full bg-[var(--color-seri-1)]" style={{ width: `${max > 0 ? Math.max(2, (cents * 100) / max) : 0}%` }} />
          </div>
        </li>
      ))}
    </ul>
  );
}
