// Ana ekran: sermayedarlar kisi kisi. Her birinin toplam sermayesi, nakitte kalani ve hangi aracta ne kadari oldugu (gercek tutar).
// Galeri kasasi dukkan sahibinin parasidir: kasayla (Havuz) alinan stoktaki araclarin odenmis kismi dukkan sahibinin malidir.
// Sermayedarin araclari "sermayeden / cepten" katkiyla ortak alinanlardir. Veri /api/net-worth (hareketlerden hesaplanir).
import { api } from "../../api/galeri";
import { SplitBar } from "../../shared/charts/Donut";
import { percent, tl, tlShort } from "../../shared/format";
import type { VehicleRef } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { ErrorBox } from "../../shared/ui/Common";
import { Plate } from "../../shared/ui/Plate";

const CASH = "var(--color-seri-1)";
const BOUND = "var(--color-seri-4)";
const MAX_PLATES = 6;

interface Row {
  id: string; name: string; owner: boolean; capital: number; cash: number; inVehicles: number; shareBp: number;
  vehicles: { vehicle: VehicleRef; cents: number }[];
}

export function PartnerCapitalCard() {
  const { data, error } = useLoad(() => api.netWorth(), []);

  const rows: Row[] = (data?.partners ?? []).filter((p) => p.capitalCents !== 0).map((p) => {
    const vehicles = (data?.vehicles ?? []).flatMap((v) => v.owners
      .filter((o) => (o.contactId === p.contactId || (p.owner && o.contactId == null)) && o.contributionCents > 0)
      .map((o) => ({ vehicle: v.vehicle, cents: o.contributionCents })));
    // Dukkan sahibinde kasayla alinan araclar da malidir; nakiti sermayesinden araclardaki kisim dusulerek bulunur
    const inVehicles = vehicles.reduce((t, x) => t + x.cents, 0);
    return {
      id: p.contactId, name: p.name, owner: p.owner, capital: p.capitalCents, shareBp: p.shareBp,
      inVehicles, cash: p.owner ? p.capitalCents - inVehicles : p.cashCents,
      vehicles: vehicles.sort((a, b) => b.cents - a.cents),
    };
  }).sort((a, b) => Number(b.owner) - Number(a.owner) || b.capital - a.capital);

  const total = rows.reduce((t, r) => t + r.capital, 0);
  const cash = rows.reduce((t, r) => t + r.cash, 0);
  const inVehicles = rows.reduce((t, r) => t + r.inVehicles, 0);
  const max = Math.max(1, ...rows.map((r) => r.capital));

  return (
    <section className="kart p-4 md:p-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="etiket">Sermayedarlar</p>
          <p className="sayi font-bold text-[26px] leading-tight mt-1">{tl(total)}</p>
        </div>
        <div className="flex gap-4 text-[13px] text-murekkep-2">
          <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-[4px]" style={{ background: CASH }} />Nakit <b className="sayi text-murekkep text-[14px]">{tlShort(cash)}</b></span>
          <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-[4px]" style={{ background: BOUND }} />Araçta <b className="sayi text-murekkep text-[14px]">{tlShort(inVehicles)}</b></span>
        </div>
      </div>
      <ErrorBox message={error} />
      {data && rows.length === 0 && <p className="text-[14px] text-murekkep-3 mt-4">Henüz sermaye girişi yok.</p>}

      <ul className="mt-4 space-y-5">
        {rows.map((r) => (
          <li key={r.id}>
            <div className="flex items-baseline justify-between gap-2">
              <span className="flex items-center gap-2 min-w-0">
                <a href={`#/contacts/${r.id}`} className="font-semibold text-[15px] truncate hover:text-plaka">{r.name}</a>
                {r.owner && <span className="text-[11px] font-semibold text-plaka-koyu bg-plaka-acik rounded px-1.5 py-0.5 shrink-0">Dükkân sahibi</span>}
              </span>
              <span className="shrink-0 text-right">
                <b className="sayi text-[18px]">{tl(r.capital)}</b>
                <span className="text-[13px] text-murekkep-3 ml-1.5">{percent(r.shareBp)}</span>
              </span>
            </div>
            <div className="mt-1.5" style={{ width: `${Math.max(4, (Math.max(0, r.capital) / max) * 100)}%` }}>
              <SplitBar height={10} parts={[
                { key: "cash", value: Math.max(0, r.cash), color: CASH },
                { key: "bound", value: r.inVehicles, color: BOUND },
              ]} />
            </div>
            <p className="mt-1 text-[13px] text-murekkep-2">
              Nakit <span className={`sayi text-[14px] ${r.cash < 0 ? "text-tehlike" : "text-murekkep"}`}>{tl(r.cash)}</span>
              <span className="mx-1.5 text-murekkep-3">·</span>
              Araçta <span className="sayi text-[14px] text-murekkep">{tl(r.inVehicles)}</span>
            </p>
            {r.vehicles.length > 0 && (
              <ul className="mt-1.5 flex flex-wrap gap-x-4 gap-y-1.5">
                {r.vehicles.slice(0, MAX_PLATES).map((x) => (
                  <li key={x.vehicle.id}>
                    <a href={`#/vehicles/${x.vehicle.id}`} className="inline-flex items-center gap-1.5 text-[13px] hover:text-plaka">
                      <Plate plate={x.vehicle.plate} size="sm" /><span className="sayi text-[14px]">{tl(x.cents)}</span>
                    </a>
                  </li>
                ))}
                {r.vehicles.length > MAX_PLATES && <li className="text-[13px] text-murekkep-2 self-center">+{r.vehicles.length - MAX_PLATES} araç</li>}
              </ul>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
