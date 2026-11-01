// Dukkan: dukkana yapilan harcamalar (kira, elektrik, yemek ...). Eklerken nereden odendigi sorulur: kasa, banka ya da
// cepten (elden; kasa degismez, kimsenin sermayesi dusmez). Ay ay toplam, odeme yerine ve kategoriye gore dagilim.
// Sadece dukkan sahibi.
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

export function ShopPage() {
  const [period, setPeriod] = useState<Period>(() => currentPeriod(todayIso()));
  const [adding, setAdding] = useState(false);
  const range = periodRange(period);
  const { data, error, reload } = useLoad(() => api.expenses(range.from, range.to), [range.from, range.to]);
  const shop = (data ?? []).filter((e) => e.type === "GENERAL_EXPENSE");
  const active = shop.filter(isActiveEntry);
  const sum = (l: LedgerEntryResponse[]) => l.reduce((t, e) => t + e.amountCents, 0);
  const pocket = active.filter((e) => e.source.type === "CAPITAL");
  const cash = active.filter((e) => e.source.type === "CASH");
  const bank = active.filter((e) => e.source.type === "BANK");

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="etiket">Dükkân giderleri</p>
          <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Dükkân</h1>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <PeriodPicker value={period} onChange={setPeriod} />
          <Button onClick={() => setAdding(true)}>Dükkân gideri ekle</Button>
        </div>
      </header>
      <ErrorBox message={error} />

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <StatTile label="Toplam" value={tl(sum(active))} tone="negative" sub={`${active.length} kayıt`} />
        <StatTile label="Kasadan" value={tl(sum(cash))} tone="neutral" sub={`${cash.length} kayıt`} />
        <StatTile label="Bankadan" value={tl(sum(bank))} tone="neutral" sub={`${bank.length} kayıt`} />
        <StatTile label="Cepten (elden)" value={tl(sum(pocket))} tone="brand" sub="kasa değişmedi" />
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:items-start">
        <section className="kart p-4 md:p-5">
          <h2 className="font-cond font-bold text-[22px] mb-3">Harcamalar</h2>
          {data && active.length === 0 && shop.length === 0 && (
            <Empty title="Bu dönemde dükkân gideri yok" description="Kira, elektrik, yemek gibi harcamaları buradan ekle." />
          )}
          {data && shop.length > 0 && <LedgerEntryList entries={shop} direction={() => -1} onChange={reload} />}
        </section>
        <section className="kart p-4 md:p-5">
          <p className="etiket mb-3">Kategoriye göre</p>
          <CategoryBars entries={active} />
        </section>
      </div>

      <ExpenseForm open={adding} shop onClose={() => setAdding(false)} onSaved={() => { setAdding(false); reload(); }} />
    </div>
  );
}
