import { api } from "../../api/galeri";
import { Donut, SplitBar } from "../../shared/charts/Donut";
import { percent, SERIES, tl } from "../../shared/format";
import { useLoad } from "../../shared/useLoad";
import { ErrorBox } from "../../shared/ui/Common";
import { Plate } from "../../shared/ui/Plate";

function compactParts(parts: { key: string; value: number; color: string }[]) {
  if (parts.length <= 6) return parts;
  return [...parts.slice(0, 5), {
    key: "other", value: parts.slice(5).reduce((sum, p) => sum + Math.max(0, p.value), 0), color: SERIES[5],
  }];
}

export function NetWorthPanel({ revision }: { revision: number }) {
  const load = useLoad(() => api.netWorth(), [revision]);
  const d = load.data;
  if (!d) return <><ErrorBox message={load.error} />{!load.error && <p>Varlıklar yükleniyor…</p>}</>;
  const slices = d.partners.filter(p => p.capitalCents > 0).map((p, i) => ({
    key: p.contactId, label: `${p.name}: ${tl(p.capitalCents)}`, value: p.capitalCents, color: SERIES[Math.min(i, 5)],
  }));
  const chart = slices.length > 6 ? [...slices.slice(0, 5), {
    key: "other", label: `Diğer: ${tl(slices.slice(5).reduce((s, p) => s + p.value, 0))}`,
    value: slices.slice(5).reduce((s, p) => s + p.value, 0), color: SERIES[5],
  }] : slices;
  return <div className="space-y-4">
    <ErrorBox message={load.error} />
    <section className="kart p-5">
      <p className="etiket">Toplam mal</p><p className="sayi text-[36px] font-bold">{tl(d.totalCents)}</p>
      <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
        {[["Kasa", d.cashCents], ["Banka", d.bankCents], ["Stok maliyeti", d.stockCents],
          ["Alacaklar", d.receivableCents], ["Verecekler", -d.payableCents]].map(([label, value]) =>
          <div key={label} className="border-t border-cizgi pt-2"><p className="text-sm text-murekkep-2">{label}</p><p className="sayi text-xl">{tl(Number(value))}</p></div>)}
      </div>
      <p className="text-sm text-murekkep-3 mt-3">Konsinye araçlar stok maliyetine dahil değildir.</p>
    </section>
    <section className="kart p-5">
      <h2 className="font-cond text-2xl font-bold">Nakit kimlere bölünmüş?</h2>
      <p className="sayi text-2xl mt-2">Kasa + banka: {tl(d.cashCents + d.bankCents)}</p>
      <p className="text-sm text-murekkep-2 mb-3">Ortakların nakitteki sermayesi ve dağıtılmamış kâr</p>
      <SplitBar parts={compactParts([
        ...d.partners.map((p, i) => ({ key: p.contactId, value: p.cashCents, color: SERIES[Math.min(i, 5)] })),
        { key: "profit", value: d.undistributedCents, color: "var(--color-basari)" },
      ])} />
      <div className="grid gap-2 sm:grid-cols-2 mt-3">{d.partners.map((p, i) => <p key={p.contactId} className="text-sm">
        <span style={{ color: SERIES[Math.min(i, 5)] }}>● </span>{p.name}: {tl(p.cashCents)}
      </p>)}<p className="text-sm">Dağıtılmamış kâr: {tl(d.undistributedCents)}</p></div>
      <p className="text-sm text-murekkep-3 mt-3">Şerit pozitif tutarların dağılımını gösterir. Sermayenin nakitteki kısmı kasa bakiyesiyle aynı değildir; alacaklar, borçlar, Havuz araçları ve açılış bakiyesi fark oluşturur.</p>
    </section>
    <section className="kart p-5">
      <h2 className="font-cond text-2xl font-bold">Kimin malı?</h2>
      <div className="flex flex-col md:flex-row gap-6 mt-4">
        <div><Donut slices={chart} ariaLabel="Ortakların sermaye dağılımı" />
          {chart.map(s => <p key={s.key} className="text-sm mt-1"><span style={{ color: s.color }}>● </span>{s.label}</p>)}
        </div>
        <div className="min-w-0 flex-1 space-y-4">{d.partners.map(p => <div key={p.contactId}>
          <div className="flex justify-between gap-2"><a href={`#/contacts/${p.contactId}`} className="font-semibold text-plaka">{p.name}</a><span>{tl(p.capitalCents)} · {percent(p.shareBp)}</span></div>
          <SplitBar parts={[{ key: "cash", value: p.cashCents, color: SERIES[0] }, { key: "stock", value: p.boundCents, color: SERIES[1] }]} />
          <p className="text-sm text-murekkep-2 mt-1">Nakit {tl(p.cashCents)} · Araçlarda bağlı {tl(p.boundCents)}</p>
          {d.vehicles.flatMap(v => v.owners.filter(o => o.contactId === p.contactId).map(o =>
            <p key={v.vehicle.id} className="text-sm mt-1"><a href={`#/vehicles/${v.vehicle.id}`} className="text-plaka">{v.vehicle.plate}</a>: bağlı katkı {tl(o.contributionCents)}</p>))}
        </div>)}</div>
      </div>
      <p className="mt-4 font-semibold">Dağıtılmamış kâr: {tl(d.undistributedCents)}</p>
      {d.openingCents !== 0 && <p className="text-sm mt-1">Açılıştan gelen varlık: {tl(d.openingCents)}</p>}
      <p className={`mt-3 border-t border-cizgi pt-3 ${d.differenceCents === 0 ? "text-basari" : "text-tehlike"}`}>
        Denge: {tl(d.totalCents)} = sermaye {tl(d.capitalCents)} + dağıtılmamış kâr {tl(d.undistributedCents)} + açılış {tl(d.openingCents)} · Fark {tl(d.differenceCents)}
      </p>
      <p className="text-sm text-murekkep-3 mt-2">Nakit sermaye, ortağın araçlarda bağlı olmayan sermayesidir. Kasa ve bankadaki para ayrıca alacaklar, borçlar ve dağıtılmamış kârdan etkilenir.</p>
    </section>
    <section className="kart p-5 space-y-4">
      <h2 className="font-cond text-2xl font-bold">Araçlar kimlere bölünmüş?</h2>
      {d.vehicles.length === 0 && <p>Stokta araç yok.</p>}
      {d.vehicles.map(v => <div key={v.vehicle.id} className="border-t border-cizgi pt-4">
        <a href={`#/vehicles/${v.vehicle.id}`} className="flex flex-wrap items-center gap-2"><Plate plate={v.vehicle.plate} size="sm" /> {v.vehicle.make} {v.vehicle.model}</a>
        <div className="mt-3 overflow-x-auto"><table className="w-full text-sm text-left"><thead><tr className="text-murekkep-3"><th>Sahip</th><th>Pay</th><th>Mal</th><th>Borç</th><th>Net</th></tr></thead>
          <tbody>{v.owners.map(o => <tr key={o.contactId ?? "pool"}>
            <td className="py-2">{o.name}</td><td>{percent(o.shareBp)}</td><td>{tl(o.assetCents)}</td><td className="text-tehlike">{tl(o.debtCents)}</td><td>{tl(o.netCents)}</td>
          </tr>)}</tbody></table></div>
        <SplitBar parts={compactParts(v.owners.map((o, i) => ({ key: o.contactId ?? "pool", value: o.shareBp, color: SERIES[Math.min(i, 5)] })))} />
        <p className="text-sm text-murekkep-2 mt-2">Maliyet {tl(v.costCents)} · Ödenmemiş senet {tl(v.debtCents)}</p>
      </div>)}
    </section>
  </div>;
}
