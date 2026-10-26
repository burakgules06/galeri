import { api } from "../../api/galeri";
import { percent, tl } from "../../shared/format";
import { useLoad } from "../../shared/useLoad";
import { ErrorBox } from "../../shared/ui/Common";

export function NetWorthPanel({ revision }: { revision: number }) {
  const { data: d, error } = useLoad(() => api.netWorth(), [revision]);
  if (!d) return <><ErrorBox message={error} />{!error && <p>Yükleniyor…</p>}</>;
  return <div className="space-y-4">
    <ErrorBox message={error} />
    {d.differenceCents !== 0 && <p role="alert" className="text-tehlike font-semibold">Hesaplarda {tl(Math.abs(d.differenceCents))} fark var</p>}
    <section className="kart p-4 md:p-5">
      <h2 className="font-cond text-2xl font-bold mb-3">Galerinin durumu</h2>
      <dl className="space-y-3">
        {([ ["Kasa", d.cashCents], ["Banka", d.bankCents], ["Stoktaki araçlar (maliyet)", d.stockCents],
          ["Alacaklar", d.receivableCents], ["Borçlar", -d.payableCents] ] as const).map(([label, value]) =>
          <div key={label} className="flex items-baseline justify-between gap-3">
            <dt>{label}</dt><dd className={`sayi text-xl shrink-0 ${label === "Borçlar" || value < 0 ? "text-tehlike" : ""}`}>{tl(value)}</dd>
          </div>)}
        <div className="border-t border-cizgi pt-3 flex justify-between items-baseline gap-3 font-bold">
          <dt>Toplam</dt><dd className="sayi text-[30px]">{tl(d.totalCents)}</dd>
        </div>
      </dl>
    </section>
    <section className="kart p-4 md:p-5">
      <h2 className="font-cond text-2xl font-bold mb-3">Ortaklar</h2>
      {d.partners.length === 0 && <p>Kayıt yok</p>}
      <ul className="divide-y divide-cizgi">{d.partners.map(p => <li key={p.contactId} className="py-3 first:pt-0">
        <div className="grid grid-cols-[minmax(0,1fr)_auto_auto] items-baseline gap-3">
          <a href={`#/contacts/${p.contactId}`} className="font-semibold text-plaka truncate">{p.name}</a>
          <span className="sayi text-xl">{tl(p.capitalCents)}</span><span className="sayi text-lg">{percent(p.shareBp)}</span>
        </div>
        {d.vehicles.flatMap(v => v.owners.filter(o => o.contactId === p.contactId && o.contributionCents !== 0).map(o =>
          <a key={v.vehicle.id} href={`#/vehicles/${v.vehicle.id}`} className="block text-sm text-murekkep-2 mt-1">
            {tl(o.contributionCents)} araçta ({v.vehicle.plate})
          </a>))}
      </li>)}</ul>
      {d.undistributedCents !== 0 && <div className="flex justify-between gap-3 border-t border-cizgi pt-3 mt-3">
        <span>Dağıtılmamış kâr</span><span className={`sayi text-xl ${d.undistributedCents < 0 ? "text-tehlike" : ""}`}>{tl(d.undistributedCents)}</span>
      </div>}
    </section>
  </div>;
}
