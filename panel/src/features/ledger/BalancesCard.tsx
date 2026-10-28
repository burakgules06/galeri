// Alacak / verecek (cari): yesil = bize borclu olanlar, kirmizi = bizim borcumuz. Net durum ve en buyuk 5 kisi.
import { useId, useState } from "react";
import { tl } from "../../shared/format";
import type { ContactBalance } from "../../shared/types";

export function BalancesCard({ receivableCents, payableCents, receivables, payables }: {
  receivableCents: number; payableCents: number; receivables: ContactBalance[]; payables: ContactBalance[];
}) {
  const net = receivableCents - payableCents;
  const [expanded, setExpanded] = useState(false);
  const detailsId = useId();

  return (
    <section className="kart overflow-hidden self-start">
      <div className="p-4 md:p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="etiket">Alacak ve verecek (toplam)</p>
          <p className={`inline-flex items-baseline gap-1.5 rounded-md px-2 py-1 text-xs font-semibold ${net > 0 ? "bg-basari-zemin text-basari" : net < 0 ? "bg-tehlike-zemin text-tehlike" : "bg-yuzey-2 text-murekkep-2"}`}>
            Net <span className="sayi text-base">{net > 0 ? "+" : net < 0 ? "−" : ""}{tl(Math.abs(net))}</span>
          </p>
        </div>

        <div className="grid grid-cols-2 gap-4 mt-4">
          <div className="min-w-0">
            <p className="text-[13px] font-semibold text-murekkep-2">Alacak</p>
            <p className="sayi font-bold text-[clamp(1.25rem,2.4vw,1.75rem)] leading-tight text-basari mt-1 break-words">{tl(receivableCents)}</p>
          </div>
          <div className="min-w-0 border-l border-cizgi pl-4">
            <p className="text-[13px] font-semibold text-murekkep-2">Verecek</p>
            <p className="sayi font-bold text-[clamp(1.25rem,2.4vw,1.75rem)] leading-tight text-tehlike mt-1 break-words">{tl(payableCents)}</p>
          </div>
        </div>
      </div>
      {expanded && <div id={detailsId} className="border-t border-cizgi p-4 md:px-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
        <BalanceList title="Bize borçlu" rows={receivables} />
        <BalanceList title="Bizim borcumuz" rows={payables} />
      </div>}
      <button type="button" aria-expanded={expanded} aria-controls={detailsId} onClick={() => setExpanded(v => !v)}
        className="basilir flex w-full items-center justify-between gap-2 border-t border-cizgi px-4 md:px-5 min-h-11 text-sm font-semibold text-plaka hover:bg-plaka-acik focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-plaka">
        {expanded ? "Detayları gizle" : "Detayları göster"}
        <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className={expanded ? "rotate-180" : ""}><path d="m6 9 6 6 6-6" /></svg>
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
                <a href={`#/contacts/${r.contactId}`} className="flex min-h-10 items-center justify-between gap-2 rounded-md text-plaka hover:bg-yuzey-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-plaka">
                  <span className="text-[14px] font-medium truncate">{r.name}</span>
                  <span className="sayi font-semibold text-[15px] shrink-0">{tl(r.amountCents)}</span>
                </a>
              </li>
            ))}
          </ul>
        )}
    </div>
  );
}
