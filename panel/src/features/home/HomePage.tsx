// Ana ekran. ADMIN: para ozeti, sermaye/alacak/verecek, son 6 ay grafikleri ve stoktaki araclar.
// STAFF: tutar yok; stok sayilari, bu ayki kendi alim-satimi ve araclar.
import { api } from "../../api/galeri";
import { useAuth } from "../../auth/useAuth";
import { BarChart, Legend } from "../../shared/charts/BarChart";
import { SplitBar } from "../../shared/charts/Donut";
import { dueLabel, SHORT_MONTHS, tl, tlShort } from "../../shared/format";
import type { DashboardResponse, VehicleSummary } from "../../shared/types";
import { Plate } from "../../shared/ui/Plate";
import { useLoad } from "../../shared/useLoad";
import { ErrorBox } from "../../shared/ui/Common";
import { StatTile } from "../../shared/ui/StatTile";
import { VehicleCard } from "../vehicles/VehicleCard";
import { PartnerCapitalCard } from "./PartnerCapitalCard";
import { HoldersShareCard } from "./HoldersShareCard";

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
  const totalCommission = data.months.reduce((t, m) => t + m.commissionCents, 0);
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
          sub={<span>
            Maliyet <b className="sayi font-semibold text-murekkep text-[14px]">{tlShort(data.stockCostCents)}</b> · ort. {data.averageDaysInStock} gün
            {data.consignmentCount > 0 && <span className="block text-konsinye font-semibold">+ {data.consignmentCount} konsinye araç</span>}
          </span>} />
        <StatTile label="Alacak" value={tl(data.receivableCents)} tone="positive" valueClassName="whitespace-normal break-words text-[23px] md:text-[28px]" sub={`${data.receivables.length} kişiden tahsil edilecek`} />
        <StatTile label="Verecek" value={tl(data.payableCents)} tone="negative" valueClassName="whitespace-normal break-words text-[23px] md:text-[28px]" sub={`${data.payables.length} kişiye ödenecek`} />
      </div>

      <PaymentsStrip data={data} />

      <PartnerCapitalCard />

      <div className="flex flex-col gap-4 lg:grid lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)] lg:items-start">
        <div className="flex min-w-0 flex-col gap-4">
          <HoldersShareCard />
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
        </div>
        <div className="flex min-w-0 flex-col gap-4">
          <section className="kart p-4 md:p-5">
            <div className="mb-2 flex flex-wrap items-end justify-between gap-2">
              <div>
                <p className="etiket">Son 6 ay · net kâr/zarar</p>
                <p className={`sayi font-bold text-[22px] leading-tight mt-0.5 ${totalProfit >= 0 ? "text-basari" : "text-tehlike"}`}>{tl(totalProfit)}</p>
              </div>
              {/* Konsinye komisyonu arac karindan ayri: sermaye karina girip girmeyecegi acik soru (MIMARI §13) */}
              {totalCommission > 0 && (
                <p className="text-right">
                  <span className="etiket block">Konsinye komisyonu</span>
                  <span className="sayi font-semibold text-[18px] text-konsinye">{tl(totalCommission)}</span>
                </p>
              )}
            </div>
            <BarChart ariaLabel="Son 6 ayda satılan araçların kârı" categories={months} height={230} minMax={0}
              format={(n) => (n === 0 ? "0" : tlShort(n * 100).replace(" ₺", ""))}
              series={[{ name: "Kâr", color: "var(--color-basari-canli)", values: data.months.map((m) => Math.max(0, m.profitCents) / 100) }]} />
          </section>
        </div>
      </div>
    </>
  );
}

// Geciken tahsilat karti (kirmizi) + bu haftanin ilk 4 taksiti. Dokununca odemeler ekrani acilir.
function PaymentsStrip({ data }: { data: DashboardResponse }) {
  const week = data.week.slice(0, 4);
  return (
    <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
      <a href="#/payments" className={`kart basilir block p-4 md:p-5 hover:shadow-md transition-shadow ${data.overdueCount ? "border-tehlike/40 bg-tehlike-zemin" : ""}`}>
        <p className="etiket flex items-center gap-1.5">
          <span className={`w-1.5 h-1.5 rounded-full ${data.overdueCount ? "bg-tehlike-canli" : "bg-basari-canli"}`} />Geciken tahsilat
        </p>
        <p className={`sayi font-bold text-[30px] leading-none tracking-tight mt-2.5 ${data.overdueCount ? "text-tehlike" : "text-basari"}`}>
          {data.overdueCount ? tl(data.overdueCents) : "Yok"}
        </p>
        <p className="text-[13px] text-murekkep-2 mt-1.5">
          {data.overdueCount ? `${data.overdueCount} taksit vadesini geçti` : "Bütün tahsilatlar zamanında"} <span className="font-semibold text-plaka">→</span>
        </p>
      </a>
      <a href="#/payments" className="kart basilir block p-4 md:p-5 hover:shadow-md transition-shadow min-w-0">
        <div className="flex items-baseline justify-between gap-3">
          <p className="etiket">Bu hafta</p>
          <p className="text-[13px] text-murekkep-2">
            Tahsilat <b className="sayi text-[15px] text-basari">{tl(data.weekCollectCents)}</b>
            {data.weekPayCents > 0 && <> · Ödeme <b className="sayi text-[15px] text-tehlike">{tl(data.weekPayCents)}</b></>}
          </p>
        </div>
        {week.length === 0
          ? <p className="text-[14px] text-murekkep-3 mt-3">7 gün içinde vadesi gelen ödeme yok.</p>
          : (
            <ul className="mt-2 divide-y divide-cizgi">
              {week.map((i) => (
                <li key={i.id} className="flex items-center gap-3 py-2">
                  <span className={`w-1.5 h-6 rounded-full shrink-0 ${i.direction === "SALE" ? "bg-basari-canli" : "bg-tehlike-canli"}`} />
                  {i.vehicle && <Plate plate={i.vehicle.plate} size="sm" />}
                  <span className="min-w-0 grow truncate text-[14px]"><b className="font-semibold">{i.payer.name}</b>
                    <span className="text-murekkep-2"> · {dueLabel(i)}</span></span>
                  <span className="sayi font-semibold text-[16px] shrink-0">{tl(i.amountCents)}</span>
                </li>
              ))}
            </ul>
          )}
      </a>
    </div>
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

