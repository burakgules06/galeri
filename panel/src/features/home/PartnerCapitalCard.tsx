// Ana ekran: sermayedarlar kisi kisi. Her ortakta toplam sermaye, nakitte duran ve araclarda bagli kismi.
// Cubuk uzunlugu en buyuk sermayeye gore; renkler: nakit seri-1, aracta seri-4. Veri /api/partners (hareketlerden hesaplanir).
import { api } from "../../api/galeri";
import { SplitBar } from "../../shared/charts/Donut";
import { percent, tl, tlShort } from "../../shared/format";
import { useLoad } from "../../shared/useLoad";
import { ErrorBox } from "../../shared/ui/Common";

const CASH = "var(--color-seri-1)";
const BOUND = "var(--color-seri-4)";

export function PartnerCapitalCard() {
  const { data, error } = useLoad(() => api.partners(), []);
  const partners = (data ?? []).filter((p) => p.capitalCents !== 0)
    .sort((a, b) => Number(b.owner) - Number(a.owner) || b.capitalCents - a.capitalCents);
  const total = partners.reduce((t, p) => t + p.capitalCents, 0);
  const cash = partners.reduce((t, p) => t + p.cashCents, 0);
  const bound = partners.reduce((t, p) => t + p.boundCents, 0);
  const max = Math.max(1, ...partners.map((p) => p.capitalCents));

  return (
    <section className="kart p-4 md:p-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="etiket">Sermayedarlar</p>
          <p className="sayi font-bold text-[26px] leading-tight mt-1">{tl(total)}</p>
        </div>
        <div className="flex gap-4 text-[13px] text-murekkep-2">
          <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-[4px]" style={{ background: CASH }} />Nakit <b className="sayi text-murekkep text-[14px]">{tlShort(cash)}</b></span>
          <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-[4px]" style={{ background: BOUND }} />Araçta <b className="sayi text-murekkep text-[14px]">{tlShort(bound)}</b></span>
        </div>
      </div>
      <ErrorBox message={error} />
      {data && partners.length === 0 && <p className="text-[14px] text-murekkep-3 mt-4">Henüz sermaye girişi yok.</p>}

      <ul className="mt-4 space-y-4">
        {partners.map((p) => (
          <li key={p.contactId}>
            <div className="flex items-baseline justify-between gap-2">
              <span className="flex items-center gap-2 min-w-0">
                <a href={`#/contacts/${p.contactId}`} className="font-semibold text-[15px] truncate hover:text-plaka">{p.name}</a>
                {p.owner && <span className="text-[11px] font-semibold text-plaka-koyu bg-plaka-acik rounded px-1.5 py-0.5 shrink-0">Dükkân sahibi</span>}
              </span>
              <span className="shrink-0 text-right">
                <b className="sayi text-[18px]">{tl(p.capitalCents)}</b>
                <span className="text-[13px] text-murekkep-3 ml-1.5">{percent(p.shareBp)}</span>
              </span>
            </div>
            <div className="mt-1.5" style={{ width: `${Math.max(4, (Math.max(0, p.capitalCents) / max) * 100)}%` }}>
              <SplitBar height={10} parts={[
                { key: "cash", value: Math.max(0, p.cashCents), color: CASH },
                { key: "bound", value: p.boundCents, color: BOUND },
              ]} />
            </div>
            <p className="mt-1 text-[13px] text-murekkep-2">
              Nakit <span className={`sayi text-[14px] ${p.cashCents < 0 ? "text-tehlike" : "text-murekkep"}`}>{tl(p.cashCents)}</span>
              <span className="mx-1.5 text-murekkep-3">·</span>
              Araçta <span className="sayi text-[14px] text-murekkep">{tl(p.boundCents)}</span>
            </p>
          </li>
        ))}
      </ul>
    </section>
  );
}
