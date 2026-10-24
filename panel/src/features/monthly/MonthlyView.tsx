// Alim ve satim: ustte kisi secimi, 12 aylik grafik (solda adet, cubuklarin ustunde ayin yildaki payi),
// altta secilen ayin araclari. Tutar yok. Yonetici herkesi, calisan sadece kendini gorur (backend filtreler).
import { useMemo, useState } from "react";
import { api } from "../../api/galeri";
import { BarChart, Legend } from "../../shared/charts/BarChart";
import { formatNumber, MONTHS, percent, shortDate, SHORT_MONTHS } from "../../shared/format";
import type { TradeItem } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { Empty, ErrorBox } from "../../shared/ui/Common";
import { Plate } from "../../shared/ui/Plate";
import { StatTile } from "../../shared/ui/StatTile";

const BOUGHT_COLOR = "var(--color-seri-1)";
const SOLD_COLOR = "var(--color-seri-2)";
const ALL = "__all__";
const NONE = "__none__";

export function MonthlyView() {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [person, setPerson] = useState(ALL);
  const [picked, setPicked] = useState<number | null>(null);
  const { data, error } = useLoad(() => api.yearlyTrades(year), [year]);

  const items = useMemo(() => (data?.items ?? []).filter((i) =>
    person === ALL ? true : person === NONE ? i.staffId == null : i.staffId === person), [data, person]);
  const monthOf = (i: TradeItem) => Number(i.date.slice(5, 7)) - 1;
  const bought = Array.from({ length: 12 }, (_, m) => items.filter((i) => i.kind === "BOUGHT" && monthOf(i) === m).length);
  const sold = Array.from({ length: 12 }, (_, m) => items.filter((i) => i.kind === "SOLD" && monthOf(i) === m).length);
  const total = items.length;
  const shares = bought.map((b, m) => (total && b + sold[m] ? percent(Math.round(((b + sold[m]) * 10000) / total), 0) : ""));

  const lastWithData = [...Array(12).keys()].reverse().find((m) => bought[m] + sold[m] > 0) ?? 11;
  const selected = picked ?? (year === now.getFullYear() ? now.getMonth() : lastWithData);
  const busiest = bought.reduce((best, b, m) => (b + sold[m] > bought[best] + sold[best] ? m : best), 0);
  const staff = data?.staff ?? [];
  const personName = person === ALL ? "Tüm galeri" : staff.find((s) => (s.id ?? NONE) === person)?.name ?? "";
  const monthItems = items.filter((i) => monthOf(i) === selected);

  const changeYear = (delta: number) => { setYear((y) => y + delta); setPicked(null); };

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="etiket">Rapor</p>
          <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Alım ve satım</h1>
        </div>
        <div className="flex items-center gap-1 kart p-1">
          <ArrowButton direction="back" label="Önceki yıl" onClick={() => changeYear(-1)} />
          <span className="sayi font-bold text-[20px] w-16 text-center">{year}</span>
          <ArrowButton direction="forward" label="Sonraki yıl" onClick={() => changeYear(1)} />
        </div>
      </header>

      {staff.length > 1 && (
        <div className="kaydir -mx-4 px-4 md:mx-0 md:px-0 overflow-x-auto">
          <div className="flex gap-2 w-max" role="radiogroup" aria-label="Kişi seç">
            <PersonChip label="Tüm galeri" active={person === ALL} onClick={() => setPerson(ALL)} />
            {staff.map((s) => (
              <PersonChip key={s.id ?? NONE} label={s.name ?? "—"} initials active={person === (s.id ?? NONE)} onClick={() => setPerson(s.id ?? NONE)} />
            ))}
          </div>
        </div>
      )}

      <ErrorBox message={error} />

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <StatTile label={`${year} alınan`} value={String(bought.reduce((a, b) => a + b, 0))} tone="brand" sub={personName} />
        <StatTile label={`${year} satılan`} value={String(sold.reduce((a, b) => a + b, 0))} sub={personName} />
        <StatTile label="En yoğun ay" value={total ? MONTHS[busiest] : "—"} sub={total ? `${bought[busiest] + sold[busiest]} işlem` : "Kayıt yok"} />
        <StatTile label="Seçili ay" value={MONTHS[selected]} sub={`${bought[selected]} alım · ${sold[selected]} satış`} />
      </div>

      <section className="kart p-4 md:p-5">
        <div className="flex flex-wrap items-end justify-between gap-2 mb-1">
          <div>
            <p className="etiket">{personName} · aylık işlem sayısı</p>
            <p className="text-[13px] text-murekkep-2 mt-1">Çubukların üstündeki yüzde, o ayın yıl içindeki payı. Detay için aya dokun.</p>
          </div>
          <Legend items={[
            { label: "Alınan", color: BOUGHT_COLOR, value: String(bought.reduce((a, b) => a + b, 0)) },
            { label: "Satılan", color: SOLD_COLOR, value: String(sold.reduce((a, b) => a + b, 0)) },
          ]} />
        </div>
        <div className="kaydir overflow-x-auto -mx-1">
          <div className="min-w-[600px] px-1">
            <BarChart ariaLabel={`${year} yılında aylara göre alınan ve satılan araç sayısı`} height={280}
              categories={SHORT_MONTHS} topLabels={shares} selected={selected} onSelect={setPicked}
              series={[{ name: "Alınan", color: BOUGHT_COLOR, values: bought }, { name: "Satılan", color: SOLD_COLOR, values: sold }]} />
          </div>
        </div>
      </section>

      <section>
        <div className="flex flex-wrap items-end justify-between gap-3 mb-3">
          <div>
            <p className="etiket">{MONTHS[selected]} {year} · {personName}</p>
            <p className="font-cond font-bold text-[24px] leading-tight mt-0.5">
              <span className="sayi text-seri-1">{bought[selected]}</span> araç aldı, <span className="sayi text-seri-2">{sold[selected]}</span> araç sattı
            </p>
          </div>
        </div>
        {monthItems.length === 0
          ? <div className="kart"><Empty title="Bu ay kayıt yok" description="Grafikte başka bir aya dokunarak o ayın araçlarını görebilirsin." /></div>
          : (
            <div className="grid gap-4 lg:grid-cols-2">
              <TradeList title="Alınanlar" color={BOUGHT_COLOR} items={monthItems.filter((i) => i.kind === "BOUGHT")} showStaff={person === ALL} />
              <TradeList title="Satılanlar" color={SOLD_COLOR} items={monthItems.filter((i) => i.kind === "SOLD")} showStaff={person === ALL} />
            </div>
          )}
      </section>
    </div>
  );
}

function TradeList({ title, color, items, showStaff }: { title: string; color: string; items: TradeItem[]; showStaff: boolean }) {
  return (
    <div className="kart overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 border-b border-cizgi">
        <p className="flex items-center gap-2 font-semibold text-[15px]">
          <span className="w-2.5 h-2.5 rounded-[3px]" style={{ background: color }} />{title}
        </p>
        <span className="sayi font-bold text-[20px]">{items.length}</span>
      </div>
      {items.length === 0
        ? <p className="px-4 py-5 text-[14px] text-murekkep-3">Yok</p>
        : (
          <ul className="divide-y divide-cizgi">
            {items.map((i) => (
              <li key={`${i.kind}-${i.vehicleId}`}>
                <a href={`#/vehicles/${i.vehicleId}`} className="flex items-center gap-3 px-4 py-3 hover:bg-yuzey-2">
                  <Plate plate={i.plate} size="sm" />
                  <span className="min-w-0 grow">
                    <span className="block font-semibold text-[15px] truncate">{i.make} <span className="font-medium">{i.model}</span></span>
                    <span className="block text-[13px] text-murekkep-2 truncate">
                      {i.modelYear} · <span className="sayi text-[14px]">{formatNumber(i.mileageKm)}</span> km
                      {showStaff && <> · <span className="font-medium text-murekkep">{i.staffName}</span></>}
                    </span>
                  </span>
                  <span className="text-[13px] font-medium text-murekkep-2 shrink-0">{shortDate(i.date)}</span>
                </a>
              </li>
            ))}
          </ul>
        )}
    </div>
  );
}

function PersonChip({ label, active, onClick, initials = false }: { label: string; active: boolean; onClick: () => void; initials?: boolean }) {
  return (
    <button type="button" role="radio" aria-checked={active} onClick={onClick}
      className={`basilir h-10 pl-1.5 pr-3.5 rounded-full border flex items-center gap-2 text-[14px] font-semibold whitespace-nowrap ${
        active ? "bg-murekkep text-white border-murekkep" : "bg-yuzey text-murekkep-2 border-cizgi-2 hover:bg-yuzey-2"} ${initials ? "" : "pl-3.5"}`}>
      {initials && (
        <span className={`w-7 h-7 rounded-full grid place-items-center font-cond font-bold text-[12px] ${active ? "bg-white/15" : "bg-plaka-acik text-plaka-koyu"}`}>
          {label.split(" ").map((w) => w[0]).slice(0, 2).join("").toLocaleUpperCase("tr-TR")}
        </span>
      )}
      {label}
    </button>
  );
}

function ArrowButton({ direction, label, onClick }: { direction: "back" | "forward"; label: string; onClick: () => void }) {
  return (
    <button onClick={onClick} aria-label={label}
      className="basilir w-9 h-9 rounded-[10px] grid place-items-center hover:bg-yuzey-2">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
        <path d={direction === "back" ? "M15 6l-6 6 6 6" : "M9 6l6 6-6 6"} />
      </svg>
    </button>
  );
}
