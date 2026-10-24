// Ana ekran. ADMIN: para ozeti, sermaye/alacak/verecek, son 6 ay grafikleri ve stoktaki araclar.
// STAFF: tutar yok; stok sayilari, bu ayki kendi alim-satimi ve araclar.
import { api } from "../../api/galeri";
import { useAuth } from "../../auth/useAuth";
import { BarChart, Legend } from "../../shared/charts/BarChart";
import { SplitBar } from "../../shared/charts/Donut";
import { SHORT_MONTHS, tl, tlShort } from "../../shared/format";
import type { VehicleSummary } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { ErrorBox } from "../../shared/ui/Common";
import { StatTile } from "../../shared/ui/StatTile";
import { BalancesCard } from "../ledger/BalancesCard";
import { CapitalCard } from "../ledger/CapitalCard";
import { VehicleCard } from "../vehicles/VehicleCard";

const BOUGHT_COLOR = "var(--color-seri-1)";
const SOLD_COLOR = "var(--color-seri-2)";

export function HomePage() {
  const { auth, isAdmin } = useAuth();
  const vehicles = useLoad(() => api.vehicles("", "IN_STOCK"), []);
  const now = new Date();
  const hour = now.getHours();
  const greeting = hour < 12 ? "Günaydın" : hour < 18 ? "İyi günler" : "İyi akşamlar";
  const firstName = (auth?.fullName ?? "").split(" ")[0];

  return (
    <div className="space-y-5 md:space-y-6">
      <header>
        <p className="etiket">{now.toLocaleDateString("tr-TR", { weekday: "long", day: "numeric", month: "long" })}</p>
        <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight mt-1">
          {greeting}{firstName && <span className="font-semibold text-murekkep-2">, {firstName}</span>}
        </h1>
      </header>

      {isAdmin ? <AdminSummary /> : <StaffSummary stock={vehicles.data ?? []} />}

      <section>
        <div className="flex items-end justify-between gap-3 mb-3">
          <div>
            <p className="etiket">Stoktaki araçlar</p>
            <p className="font-cond font-bold text-[22px] leading-tight mt-0.5">
              {vehicles.data ? <><span className="sayi">{vehicles.data.length}</span> araç satışta</> : "…"}
            </p>
          </div>
          <a href="#/vehicles" className="text-[14px] font-semibold text-plaka hover:text-plaka-koyu">Tümünü gör →</a>
        </div>
        <ErrorBox message={vehicles.error} />
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {(vehicles.data ?? []).map((v) => <VehicleCard key={v.id} v={v} />)}
        </div>
      </section>
    </div>
  );
}

function AdminSummary() {
  const { data, error } = useLoad(() => api.dashboard(), []);
  if (error) return <ErrorBox message={error} />;
  if (!data) return <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">{[0, 1, 2, 3].map((i) => <div key={i} className="kart h-[118px] animate-pulse" />)}</div>;

  const money = data.cashCents + data.bankCents;
  const months = data.months.map((m) => SHORT_MONTHS[m.month - 1]);
  const totalProfit = data.months.reduce((t, m) => t + m.profitCents, 0);
  const totalSold = data.months.reduce((t, m) => t + m.sold, 0);

  return (
    <>
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <StatTile label="Kasa ve banka" value={tlShort(money)} tone="brand"
          sub={<span>Kasa <b className="sayi font-semibold text-murekkep text-[14px]">{tlShort(data.cashCents)}</b> · Banka <b className="sayi font-semibold text-murekkep text-[14px]">{tlShort(data.bankCents)}</b></span>}>
          <SplitBar height={6} parts={[
            { key: "c", value: data.cashCents, color: "var(--color-seri-1)" },
            { key: "b", value: data.bankCents, color: "var(--color-seri-3)" },
          ]} />
        </StatTile>
        <StatTile label="Stok" value={`${data.inStockCount} araç`}
          sub={<span>Maliyet <b className="sayi font-semibold text-murekkep text-[14px]">{tlShort(data.stockCostCents)}</b> · ort. {data.averageDaysInStock} gün</span>} />
        <StatTile label="Alacak" value={tlShort(data.receivableCents)} tone="positive" sub={`${data.receivables.length} kişiden tahsil edilecek`} />
        <StatTile label="Verecek" value={tlShort(data.payableCents)} tone="negative" sub={`${data.payables.length} kişiye ödenecek`} />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <CapitalCard partners={data.partners} totalCents={data.capitalCents} />
        <BalancesCard receivableCents={data.receivableCents} payableCents={data.payableCents}
          receivables={data.receivables} payables={data.payables} />
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <section className="kart p-4 md:p-5">
          <div className="flex flex-wrap items-end justify-between gap-2 mb-2">
            <div>
              <p className="etiket">Son 6 ay · alım ve satım</p>
              <p className="font-cond font-bold text-[22px] leading-tight mt-0.5"><span className="sayi">{totalSold}</span> araç satıldı</p>
            </div>
            <Legend items={[{ label: "Alınan", color: BOUGHT_COLOR }, { label: "Satılan", color: SOLD_COLOR }]} />
          </div>
          <BarChart ariaLabel="Son 6 ayda alınan ve satılan araç sayısı" categories={months} height={230}
            series={[
              { name: "Alınan", color: BOUGHT_COLOR, values: data.months.map((m) => m.bought) },
              { name: "Satılan", color: SOLD_COLOR, values: data.months.map((m) => m.sold) },
            ]} />
        </section>

        <section className="kart p-4 md:p-5">
          <div className="mb-2">
            <p className="etiket">Son 6 ay · satış kârı</p>
            <p className={`sayi font-bold text-[22px] leading-tight mt-0.5 ${totalProfit >= 0 ? "text-basari" : "text-tehlike"}`}>{tl(totalProfit)}</p>
          </div>
          <BarChart ariaLabel="Son 6 ayda satılan araçların kârı" categories={months} height={230} minMax={0}
            format={(n) => (n === 0 ? "0" : tlShort(n * 100).replace(" ₺", ""))}
            series={[{ name: "Kâr", color: "var(--color-basari-canli)", values: data.months.map((m) => Math.max(0, m.profitCents) / 100) }]} />
        </section>
      </div>
    </>
  );
}

function StaffSummary({ stock }: { stock: VehicleSummary[] }) {
  const now = new Date();
  const { data } = useLoad(() => api.yearlyTrades(now.getFullYear()), []);
  const month = now.getMonth() + 1;
  const thisMonth = (data?.items ?? []).filter((i) => Number(i.date.slice(5, 7)) === month);
  const average = stock.length ? Math.round(stock.reduce((t, v) => t + v.daysInStock, 0) / stock.length) : 0;
  const old = stock.filter((v) => v.daysInStock >= 60).length;

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
      <StatTile label="Stokta" value={`${stock.length} araç`} tone="brand" />
      <StatTile label="Ortalama stok" value={`${average} gün`} sub={old ? `${old} araç 60 günü geçti` : "60 günü geçen yok"} tone={old ? "negative" : "neutral"} />
      <StatTile label="Bu ay aldığım" value={String(thisMonth.filter((i) => i.kind === "BOUGHT").length)} />
      <StatTile label="Bu ay sattığım" value={String(thisMonth.filter((i) => i.kind === "SOLD").length)} tone="positive" />
    </div>
  );
}
