// Alacak / verecek (cari): yesil = bize borclu olanlar, kirmizi = bizim borcumuz. Net durum ve en buyuk 5 kisi.
import { useState } from "react";
import { tl } from "../../shared/format";
import type { ContactBalance } from "../../shared/types";

export function BalancesCard({ receivableCents, payableCents, receivables, payables }: {
  receivableCents: number; payableCents: number; receivables: ContactBalance[]; payables: ContactBalance[];
}) {
  const net = receivableCents - payableCents;
  const [expanded, setExpanded] = useState(false);

  return (
    <section className="kart p-4 md:p-5">
      <div className="flex items-baseline justify-between gap-3">
        <p className="etiket">Alacak ve verecek</p>
        <p className="text-[13px] text-murekkep-2">Net <span className={`sayi font-bold text-[17px] ${net >= 0 ? "text-basari" : "text-tehlike"}`}>{net >= 0 ? "+" : "−"}{tl(Math.abs(net))}</span></p>
      </div>

      <div className="grid grid-cols-2 gap-3 mt-3">
        <div>
          <p className="text-[13px] font-semibold text-basari">Alacak</p>
          <p className="sayi font-bold text-[24px] md:text-[26px] leading-tight text-basari">{tl(receivableCents)}</p>
        </div>
        <div className="text-right">
          <p className="text-[13px] font-semibold text-tehlike">Verecek</p>
          <p className="sayi font-bold text-[24px] md:text-[26px] leading-tight text-tehlike">{tl(payableCents)}</p>
        </div>
      </div>
      {expanded && <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <BalanceList title="Bize borçlu" rows={receivables} />
        <BalanceList title="Bizim borcumuz" rows={payables} />
      </div>}
      <button type="button" aria-expanded={expanded} onClick={() => setExpanded(v => !v)}
        className="basilir min-h-10 mt-3 text-sm font-semibold text-plaka">
        {expanded ? "Gizle ▴" : "Detayları göster ▾"}
      </button>
    </section>
  );
}

function BalanceList({ title, rows }: { title: string; rows: ContactBalance[] }) {
  return (
    <div>
      <p className="etiket mb-1.5">{title}</p>
      {rows.length === 0
        ? <p className="text-[13.5px] text-murekkep-3">Kayıt yok</p>
        : (
          <ul className="space-y-2.5">
            {rows.map((r) => (
              <li key={r.contactId}>
                <div className="flex items-baseline justify-between gap-2">
                  <span className="text-[14px] font-semibold truncate">{r.name}</span>
                  <span className="sayi font-semibold text-[15px] shrink-0">{tl(r.amountCents)}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
    </div>
  );
}
