// Sermayedarlarin hisseleri: herkesin sermayesinin toplamdaki payi (halka grafik). Dukkan sahibi ilk sirada.
import { useState } from "react";
import { api } from "../../api/galeri";
import { Donut } from "../../shared/charts/Donut";
import { percent, SERIES, tl, tlShort } from "../../shared/format";
import { useLoad } from "../../shared/useLoad";
import { ErrorBox } from "../../shared/ui/Common";

export function HoldersShareCard() {
  const { data, error } = useLoad(() => api.holders(), []);
  const [active, setActive] = useState<string | null>(null);
  const rows = (data ?? []).filter((h) => h.capitalCents > 0);
  const total = rows.reduce((t, r) => t + r.capitalCents, 0);
  const colored = rows.map((r, i) => ({
    key: r.contactId ?? "owner", name: r.name, owner: r.owner, capital: r.capitalCents, color: SERIES[i % SERIES.length],
    bp: total > 0 ? Math.round((r.capitalCents * 10000) / total) : 0,
  }));
  const focus = colored.find((r) => r.key === active);

  return (
    <section className="kart p-4 md:p-5">
      <div className="flex items-baseline justify-between gap-3">
        <div>
          <p className="etiket">Hisseler</p>
          <p className="sayi font-bold text-[26px] leading-tight mt-1">{tl(total)}</p>
        </div>
        <p className="text-[13px] text-murekkep-2">{colored.length} kişi</p>
      </div>
      <ErrorBox message={error} />
      {data && colored.length === 0 && <p className="text-[14px] text-murekkep-3 mt-4">Henüz sermaye girişi yok.</p>}

      {colored.length > 0 && (
        <div className="mt-4 flex flex-col sm:flex-row sm:items-center gap-5">
          <div className="mx-auto sm:mx-0">
            <Donut ariaLabel="Sermayedarların hisseleri" active={active} onActive={setActive}
              slices={colored.map((r) => ({ key: r.key, label: `${r.name} ${percent(r.bp)}`, value: r.capital, color: r.color }))}
              center={focus
                ? <><span className="sayi font-bold text-[26px] leading-none">{percent(focus.bp)}</span>
                  <span className="text-[12px] text-murekkep-2 mt-1 max-w-[110px] truncate">{focus.name}</span></>
                : <><span className="sayi font-bold text-[22px] leading-none">{tlShort(total)}</span>
                  <span className="etiket mt-1.5">Toplam</span></>} />
          </div>
          <ul className="grow min-w-0 space-y-1">
            {colored.map((r) => (
              <li key={r.key} onMouseEnter={() => setActive(r.key)} onMouseLeave={() => setActive(null)}
                className={`rounded-lg px-2.5 py-2 -mx-1 transition-colors ${active === r.key ? "bg-yuzey-2" : ""}`}>
                <div className="flex items-center gap-2.5">
                  <span className="w-3 h-3 rounded-[4px] shrink-0" style={{ background: r.color }} />
                  <span className="font-semibold text-[15px] truncate">{r.name}</span>
                  {r.owner && <span className="text-[11px] font-semibold text-plaka-koyu bg-plaka-acik rounded px-1.5 py-0.5 shrink-0">Dükkân sahibi</span>}
                  <span className="sayi font-bold text-[19px] ml-auto pl-2 whitespace-nowrap">{percent(r.bp)}</span>
                </div>
                <div className="pl-[22px] mt-0.5 sayi text-[14px] text-murekkep">{tl(r.capital)}</div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
