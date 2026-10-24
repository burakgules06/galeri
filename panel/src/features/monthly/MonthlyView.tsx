// Calisan bazinda aylik alim-satim. Tutar yok. Yonetici herkesi, calisan sadece kendini gorur.
import { useState } from "react";
import { api } from "../../api/galeri";
import { MONTHS, shortDate } from "../../shared/format";
import type { MonthlyVehicle, StaffMonthly } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { Plate } from "../../shared/ui/Plate";
import { Empty, ErrorBox } from "../../shared/ui/Common";

export function MonthlyView() {
  const now = new Date();
  const [period, setPeriod] = useState({ year: now.getFullYear(), month: now.getMonth() + 1 });
  const { data, error } = useLoad(() => api.monthlyTrades(period.year, period.month), [period.year, period.month]);

  const shift = (delta: number) => setPeriod(({ year, month }) => {
    const t = new Date(year, month - 1 + delta, 1);
    return { year: t.getFullYear(), month: t.getMonth() + 1 };
  });

  const totalBought = data?.staff.reduce((s, r) => s + r.bought.length, 0) ?? 0;
  const totalSold = data?.staff.reduce((s, r) => s + r.sold.length, 0) ?? 0;

  return (
    <div>
      <div className="flex items-end justify-between gap-3 mb-4">
        <h1 className="font-cond font-bold text-[30px] leading-none tracking-tight">Alım ve satım</h1>
      </div>

      <div className="flex flex-wrap items-center gap-x-2 gap-y-3 mb-4">
        <ArrowButton direction="back" onClick={() => shift(-1)} />
        <p className="font-cond font-bold text-[24px] min-w-[160px] text-center">{MONTHS[period.month - 1]} {period.year}</p>
        <ArrowButton direction="forward" onClick={() => shift(1)} />
        {data && (
          <p className="w-full md:w-auto md:ml-auto text-murekkep-2 text-[15px]">
            <span className="font-cond font-bold text-murekkep text-[19px]">{totalBought}</span> alındı,{" "}
            <span className="font-cond font-bold text-murekkep text-[19px]">{totalSold}</span> satıldı
          </p>
        )}
      </div>

      <ErrorBox message={error} />
      {data && data.staff.length === 0 && <Empty title="Bu ay kayıt yok" />}

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {data?.staff.map((s) => <StaffCard key={s.staffId ?? "none"} s={s} />)}
      </div>
    </div>
  );
}

function StaffCard({ s }: { s: StaffMonthly }) {
  const [open, setOpen] = useState(false);
  const empty = s.bought.length === 0 && s.sold.length === 0;
  return (
    <section className="bg-yuzey rounded-xl border border-cizgi">
      <button type="button" onClick={() => setOpen((x) => !x)} disabled={empty}
        className="basilir w-full flex items-center gap-4 p-4 text-left disabled:cursor-default" aria-expanded={open}>
        <span className="w-11 h-11 rounded-full bg-plaka-acik text-plaka-koyu font-cond font-bold text-[18px] grid place-items-center shrink-0">
          {s.name.split(" ").map((w) => w[0]).slice(0, 2).join("").toLocaleUpperCase("tr-TR")}
        </span>
        <span className="grow font-semibold text-[17px] truncate">{s.name}</span>
        <Count n={s.bought.length} label="aldı" />
        <Count n={s.sold.length} label="sattı" />
      </button>
      {open && (
        <div className="border-t border-cizgi px-4 py-3 space-y-3">
          <VehicleGroup title="Aldıkları" vehicles={s.bought} />
          <VehicleGroup title="Sattıkları" vehicles={s.sold} />
        </div>
      )}
    </section>
  );
}

function Count({ n, label }: { n: number; label: string }) {
  return (
    <span className="text-center w-12">
      <span className={`block font-cond font-bold text-[26px] leading-none ${n ? "text-murekkep" : "text-murekkep-3"}`}>{n}</span>
      <span className="block text-[12px] text-murekkep-2">{label}</span>
    </span>
  );
}

function VehicleGroup({ title, vehicles }: { title: string; vehicles: MonthlyVehicle[] }) {
  if (!vehicles.length) return null;
  return (
    <div>
      <h3 className="text-[13px] font-semibold text-murekkep-2 mb-1.5">{title}</h3>
      <ul className="space-y-1.5">
        {vehicles.map((v) => (
          <li key={v.vehicleId}>
            <a href={`#/vehicles/${v.vehicleId}`} className="flex items-center gap-2.5 hover:bg-yuzey-2 rounded-lg -mx-1 px-1 py-1">
              <Plate plate={v.plate} size="sm" />
              <span className="grow truncate text-[15px]">{v.make} {v.model}</span>
              <span className="text-[13px] text-murekkep-3">{shortDate(v.date)}</span>
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}

function ArrowButton({ direction, onClick }: { direction: "back" | "forward"; onClick: () => void }) {
  return (
    <button onClick={onClick} aria-label={direction === "back" ? "Önceki ay" : "Sonraki ay"}
      className="basilir w-10 h-10 rounded-lg bg-yuzey border border-cizgi grid place-items-center hover:bg-yuzey-2">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
        <path d={direction === "back" ? "M15 6l-6 6 6 6" : "M9 6l6 6-6 6"} />
      </svg>
    </button>
  );
}
