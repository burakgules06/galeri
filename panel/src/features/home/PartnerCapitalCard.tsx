// Sermayedarlar kisi kisi (/api/holders): toplam sermaye, nakitte kalani, hangi aracta ne kadari (gercek tutar).
// Dukkan sahibi havuzdan kalandir: kasa + bankalar + araclar + alacaklar - borclar - sermayedarlarin parasi.
import { api } from "../../api/galeri";
import { SplitBar } from "../../shared/charts/Donut";
import { percent, tl, tlShort } from "../../shared/format";
import { useLoad } from "../../shared/useLoad";
import { ErrorBox } from "../../shared/ui/Common";
import { Plate } from "../../shared/ui/Plate";

const CASH = "var(--color-seri-1)";
const BOUND = "var(--color-seri-4)";
const MAX_PLATES = 6;

export function PartnerCapitalCard({ revision = 0 }: { revision?: number }) {
  const { data, error } = useLoad(() => api.holders(), [revision]);
  const rows = (data ?? []).filter((h) => h.owner || h.capitalCents !== 0);

  const total = rows.reduce((t, r) => t + r.capitalCents, 0);
  const cash = rows.reduce((t, r) => t + r.cashCents, 0);
  const inVehicles = rows.reduce((t, r) => t + r.vehicleCents, 0);
  const max = Math.max(1, ...rows.map((r) => r.capitalCents));

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

      <ul className="mt-4 space-y-5">
        {rows.map((r) => (
          <li key={r.contactId ?? "owner"}>
            <div className="flex items-baseline justify-between gap-2">
              <span className="flex items-center gap-2 min-w-0">
                {r.contactId
                  ? <a href={`#/contacts/${r.contactId}`} className="font-semibold text-[15px] truncate hover:text-plaka">{r.name}</a>
                  : <span className="font-semibold text-[15px] truncate">{r.name}</span>}
                {r.owner && <span className="text-[11px] font-semibold text-plaka-koyu bg-plaka-acik rounded px-1.5 py-0.5 shrink-0">Dükkân sahibi</span>}
              </span>
              <span className="shrink-0 text-right">
                <b className={`sayi text-[18px] ${r.capitalCents < 0 ? "text-tehlike" : ""}`}>{tl(r.capitalCents)}</b>
                <span className="text-[13px] text-murekkep-3 ml-1.5">{percent(r.shareBp)}</span>
              </span>
            </div>
            <div className="mt-1.5" style={{ width: `${Math.max(4, (Math.max(0, r.capitalCents) / max) * 100)}%` }}>
              <SplitBar height={10} parts={[
                { key: "cash", value: Math.max(0, r.cashCents), color: CASH },
                { key: "bound", value: r.vehicleCents, color: BOUND },
              ]} />
            </div>
            <p className="mt-1 text-[13px] text-murekkep-2">
              Nakit <span className={`sayi text-[14px] ${r.cashCents < 0 ? "text-tehlike" : "text-murekkep"}`}>{tl(r.cashCents)}</span>
              <span className="mx-1.5 text-murekkep-3">·</span>
              Araçta <span className="sayi text-[14px] text-murekkep">{tl(r.vehicleCents)}</span>
              {r.otherCents !== 0 && <>
                <span className="mx-1.5 text-murekkep-3">·</span>
                Alacak / borç <span className={`sayi text-[14px] ${r.otherCents < 0 ? "text-tehlike" : "text-murekkep"}`}>{tl(r.otherCents)}</span>
              </>}
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
