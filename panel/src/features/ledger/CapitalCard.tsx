// Sermaye ve ortaklar: halka grafik + ortak listesi. Dukkan sahibi ilk sirada ve rozetli.
// Her ortak icin: sermayesi, toplamdaki payi ve (sahip disindakiler icin) sahibin sermayesine orani.
import { useState } from "react";
import { percent, SERIES, tl, tlShort } from "../../shared/format";
import type { PartnerShare } from "../../shared/types";
import { Donut } from "../../shared/charts/Donut";

export function CapitalCard({ partners, totalCents }: { partners: PartnerShare[]; totalCents: number }) {
  const [active, setActive] = useState<string | null>(null);
  const owner = partners.find((p) => p.owner);
  const colored = partners.map((p, i) => ({ ...p, color: SERIES[i % SERIES.length] }));
  const focus = colored.find((p) => p.contactId === active);

  return (
    <section className="kart p-4 md:p-5">
      <div className="flex items-baseline justify-between gap-3">
        <div>
          <p className="etiket">Sermaye ve ortaklar</p>
          <p className="sayi font-bold text-[26px] leading-tight mt-1">{tl(totalCents)}</p>
        </div>
        <p className="text-[13px] text-murekkep-2">{partners.length} ortak</p>
      </div>

      {partners.length === 0
        ? <p className="text-[14px] text-murekkep-3 mt-4">Henüz sermaye girişi yok.</p>
        : (
          <div className="mt-4 flex flex-col sm:flex-row sm:items-center gap-5">
            <div className="mx-auto sm:mx-0">
              <Donut ariaLabel="Ortakların sermaye payları" active={active} onActive={setActive}
                slices={colored.map((p) => ({ key: p.contactId, label: `${p.name} ${percent(p.shareBasisPoints)}`, value: p.capitalCents, color: p.color }))}
                center={focus
                  ? <><span className="sayi font-bold text-[26px] leading-none">{percent(focus.shareBasisPoints)}</span>
                    <span className="text-[12px] text-murekkep-2 mt-1 max-w-[110px] truncate">{focus.name}</span></>
                  : <><span className="sayi font-bold text-[22px] leading-none">{tlShort(totalCents)}</span>
                    <span className="etiket mt-1.5">Toplam</span></>} />
            </div>

            <ul className="grow min-w-0 space-y-1">
              {colored.map((p) => {
                const ofOwner = owner && !p.owner && owner.capitalCents > 0
                  ? Math.round((p.capitalCents * 10000) / owner.capitalCents) : null;
                return (
                  <li key={p.contactId} onMouseEnter={() => setActive(p.contactId)} onMouseLeave={() => setActive(null)}
                    className={`rounded-lg px-2.5 py-2 -mx-1 transition-colors ${active === p.contactId ? "bg-yuzey-2" : ""}`}>
                    <div className="flex items-center gap-2.5">
                      <span className="w-3 h-3 rounded-[4px] shrink-0" style={{ background: p.color }} />
                      <span className="font-semibold text-[15px] truncate">{p.name}</span>
                      {p.owner && <span className="text-[11px] font-semibold text-plaka-koyu bg-plaka-acik rounded px-1.5 py-0.5 shrink-0">Dükkân sahibi</span>}
                      <span className="sayi font-bold text-[19px] ml-auto pl-2">{percent(p.shareBasisPoints)}</span>
                    </div>
                    <div className="flex items-center justify-between gap-2 pl-[22px] mt-0.5 text-[12.5px] text-murekkep-2">
                      <span className="sayi text-[14px] text-murekkep">{tl(p.capitalCents)}</span>
                      {ofOwner != null && <span>sahibe oranı <b className="font-semibold text-murekkep">{percent(ofOwner, 0)}</b></span>}
                    </div>
                  </li>
                );
              })}
            </ul>
          </div>
        )}
    </section>
  );
}
