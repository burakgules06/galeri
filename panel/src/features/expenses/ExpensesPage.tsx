// Masraflar: girilen butun masraflar (arac masrafi ve dukkan / genel gider) ay ay. Ustte toplamlar ve kategoriye gore
// dagilim; listede duzenle / sil (ters kayit). Sadece dukkan sahibi.
import { useState } from "react";
import { api } from "../../api/galeri";
import { tl, todayIso } from "../../shared/format";
import type { LedgerEntryResponse } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox } from "../../shared/ui/Common";
import { StatTile } from "../../shared/ui/StatTile";
import { ExpenseForm } from "../ledger/ExpenseForm";
import { LedgerEntryList } from "../ledger/LedgerEntryList";
import { CategoryBars } from "./CategoryBars";
import { PeriodPicker } from "./PeriodPicker";
import { currentPeriod, isActiveEntry, periodRange, type Period } from "./period";

type Kind = "all" | "VEHICLE_EXPENSE" | "GENERAL_EXPENSE";

export function ExpensesPage() {
  const [period, setPeriod] = useState<Period>(() => currentPeriod(todayIso()));
  const [kind, setKind] = useState<Kind>("all");
  const [adding, setAdding] = useState(false);
  const range = periodRange(period);
  const { data, error, reload } = useLoad(() => api.expenses(range.from, range.to), [range.from, range.to]);
  const all = data ?? [];
  const active = all.filter(isActiveEntry);
  const sum = (l: LedgerEntryResponse[]) => l.reduce((t, e) => t + e.amountCents, 0);
  const vehicle = active.filter((e) => e.type === "VEHICLE_EXPENSE");
  const general = active.filter((e) => e.type === "GENERAL_EXPENSE");
  const shown = kind === "all" ? all : all.filter((e) => e.type === kind);
  const shownActive = shown.filter(isActiveEntry);

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="etiket">Giderler</p>
          <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Masraflar</h1>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <PeriodPicker value={period} onChange={setPeriod} />
          <Button onClick={() => setAdding(true)}>Masraf ekle</Button>
        </div>
      </header>
      <ErrorBox message={error} />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <StatTile label="Toplam masraf" value={tl(sum(active))} tone="negative" sub={`${active.length} kayıt`} />
        <StatTile label="Araç masrafları" value={tl(sum(vehicle))} tone="neutral" sub={`${vehicle.length} kayıt · araç maliyetine`} />
        <StatTile label="Dükkân giderleri" value={tl(sum(general))} tone="neutral" sub={`${general.length} kayıt · dönem kârından`} />
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:items-start">
        <section className="kart p-4 md:p-5">
          <div className="flex gap-1 rounded-[12px] bg-yuzey-2 p-1 mb-4 w-fit max-w-full overflow-x-auto" role="tablist">
            {([["all", "Tümü"], ["VEHICLE_EXPENSE", "Araç masrafları"], ["GENERAL_EXPENSE", "Dükkân giderleri"]] as const).map(([v, label]) => (
              <button key={v} type="button" role="tab" aria-selected={kind === v} onClick={() => setKind(v)}
                className={`basilir shrink-0 h-9 px-3.5 rounded-[9px] text-[14px] font-semibold whitespace-nowrap ${
                  kind === v ? "bg-yuzey text-murekkep shadow-sm" : "text-murekkep-2 hover:text-murekkep"}`}>{label}</button>
            ))}
          </div>
          {data && shownActive.length === 0 && <Empty title="Bu dönemde masraf yok" />}
          {data && shown.length > 0 && <LedgerEntryList entries={shown} direction={() => -1} onChange={reload} />}
        </section>
        <section className="kart p-4 md:p-5">
          <p className="etiket mb-3">Kategoriye göre</p>
          <CategoryBars entries={shownActive} />
        </section>
      </div>

      <ExpenseForm open={adding} onClose={() => setAdding(false)} onSaved={() => { setAdding(false); reload(); }} />
    </div>
  );
}
