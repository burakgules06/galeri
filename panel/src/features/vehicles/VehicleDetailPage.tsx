import { useState, type ReactNode } from "react";
import { api, isAdminDetail } from "../../api/galeri";
import { formatDate, formatNumber, FUEL, percent, STATUS, tl, TRANSMISSION } from "../../shared/format";
import type { BodyPart, ContactSummary, ShareResponse, PartCondition, VehicleDetail, VehicleDetailAdmin, VehicleStatus } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Plate } from "../../shared/ui/Plate";
import { Button, ErrorBox } from "../../shared/ui/Common";
import { DamageMap } from "./DamageMap";
import { DaysInStock } from "./DaysInStock";
import { OwnershipBadge } from "./OwnershipBadge";
import { PaymentPlanCard } from "./PaymentPlanCard";
import { SaleForm } from "./SaleForm";
import { VehicleForm } from "./VehicleForm";
import { BulkExpenseForm } from "../ledger/BulkExpenseForm";
import { LedgerEntryList } from "../ledger/LedgerEntryList";

// Durum renkleri: stokta (onayli, mavi), satildi (olumlu, yesil), kapandi (gri)
const STATUS_PILL: Record<VehicleStatus, string> = {
  IN_STOCK: "bg-plaka-acik text-plaka-koyu",
  SOLD: "bg-basari-zemin text-basari",
  CLOSED: "bg-yuzey-2 text-murekkep-2 border border-cizgi",
};

export function VehicleDetailPage({ id }: { id: string }) {
  const { data, setData, error, reload } = useLoad(() => api.vehicle(id), [id]);
  const [form, setForm] = useState<"edit" | "sell" | null>(null);
  const [removeError, setRemoveError] = useState<string | null>(null);

  if (error) return <ErrorBox message={error} />;
  if (!data) return <p className="text-murekkep-2 py-10 text-center">Yükleniyor…</p>;

  const admin = isAdminDetail(data) ? data : null;
  const v: VehicleDetail = admin ? admin.vehicle : (data as VehicleDetail);

  // Yanlis giris: alis, odemeler, masraflar ve ortak katkilari terslenir, para geldigi yere doner
  const remove = async () => {
    if (!window.confirm(`${v.plate} kaldırılsın mı? Alış, masraflar ve ödemeler geri alınır, para geldiği hesaba döner.`)) return;
    setRemoveError(null);
    try {
      await api.removeVehicle(v.id);
      window.location.hash = "#/vehicles";
    } catch (e) {
      setRemoveError(errorMessage(e));
    }
  };

  return (
    <div>
      <a href="#/vehicles" className="inline-flex items-center gap-1 text-murekkep-2 font-semibold text-[15px] mb-3 hover:text-murekkep">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M15 6l-6 6 6 6" /></svg>
        Araçlar
      </a>

      <header className="flex flex-col md:flex-row md:items-end md:justify-between gap-4 mb-5">
        <div>
          <Plate plate={v.plate} size="lg" />
          <h1 className="font-cond text-[32px] md:text-[36px] leading-tight tracking-tight mt-3">
            <span className="font-bold">{v.make}</span> <span className="font-medium text-murekkep-2">{v.model}</span>
          </h1>
          <div className="flex flex-wrap items-center gap-2.5 mt-1.5">
            <span className="sayi font-semibold text-[18px]">{v.modelYear}</span>
            <span className={`text-[12.5px] font-semibold rounded-full px-2.5 py-1 ${STATUS_PILL[v.status]}`}>{STATUS[v.status]}</span>
            <OwnershipBadge ownership={v.ownership} className="py-1 px-2 text-[12.5px]" />
            <DaysInStock days={v.daysInStock} status={v.status} />
          </div>
        </div>
        {admin && (
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setForm("edit")}>Düzenle</Button>
            {v.status === "IN_STOCK" && <Button variant="quiet" className="text-tehlike" onClick={remove}>Kaldır</Button>}
            {v.status === "IN_STOCK" && <Button onClick={() => setForm("sell")}>Satıldı olarak işaretle</Button>}
          </div>
        )}
      </header>
      <ErrorBox message={removeError} />

      <div className="grid gap-4 lg:grid-cols-[1fr_380px]">
        <div className="space-y-4">
          <Card title="Teknik bilgiler">
            <dl className="grid grid-cols-2 sm:grid-cols-3 gap-x-4 gap-y-3">
              <Info label="Kilometre" value={`${formatNumber(v.mileageKm)} km`} numeric />
              <Info label="Yakıt" value={FUEL[v.fuel]} />
              <Info label="Vites" value={TRANSMISSION[v.transmission]} />
              <Info label="Renk" value={v.color ?? "—"} />
              <Info label="Tramer" value={v.tramerCents ? tl(v.tramerCents) : "Yok"} numeric />
              <Info label="Alış tarihi" value={formatDate(v.purchaseDate)} />
              {v.saleDate && <Info label="Satış tarihi" value={formatDate(v.saleDate)} />}
            </dl>
            {v.notes && <p className="mt-4 pt-3 border-t border-cizgi text-[15px] whitespace-pre-line">{v.notes}</p>}
          </Card>

          {admin && (
            <Card title="Alış ve satış">
              <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
                {v.ownership === "CONSIGNMENT" ? <>
                  <ContactInfo label="Aracın sahibi (konsinye)" contact={admin.consignor} link={false} />
                  <Info label="Alan çalışan" value={admin.boughtBy?.name ?? "—"} />
                  <Info label="Komisyon" numeric strong value={admin.commissionType === "RATE"
                    ? percent(admin.commissionRateBp ?? 0, 2) : tl(admin.commissionCents)} />
                </> : <>
                  <Info label="Kimden alındı" value={admin.seller?.name ?? "—"} />
                  <Info label="Alan çalışan" value={admin.boughtBy?.name ?? "—"} />
                  <Info label="Gerçek alış bedeli" value={tl(admin.actualPurchaseCents)} numeric strong />
                  <Info label="Resmi alış bedeli" value={tl(admin.officialPurchaseCents)} numeric />
                </>}
                {v.ownership === "CO_OWNED" && (
                  <div className="col-span-2">
                    <dt className="etiket">Ortaklar</dt>
                    <dd className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-[15px]">
                      {admin.shares.map((s) => (
                        <span key={s.userId} className="font-semibold">
                          {s.name} <span className="sayi text-[16px] text-ortakli">{percent(s.shareBp)}</span>
                        </span>
                      ))}
                    </dd>
                  </div>
                )}
                {v.status !== "IN_STOCK" && <>
                  <Info label="Alıcı" value={admin.buyer?.name ?? "—"} />
                  <Info label="Satan çalışan" value={admin.soldBy?.name ?? "—"} />
                  <Info label="Satış bedeli" value={tl(admin.salePriceCents)} numeric strong />
                </>}
              </dl>
            </Card>
          )}

          {admin && (
            <CostCard key={`${admin.actualPurchaseCents}-${admin.salePriceCents}-${admin.purchaseAccountId}-${v.ownership}-${admin.commissionRateBp}-${admin.commissionCents}`}
              vehicle={v} ownerName={admin.consignor?.name} />
          )}

          {admin && v.ownership !== "CONSIGNMENT" && (
            <PaymentPlanCard key={`purchase-${admin.actualPurchaseCents}-${admin.seller?.id}-${v.purchaseDate}`} vehicleId={v.id}
              direction="PURCHASE" onChanged={reload} />
          )}

          {admin && v.status !== "IN_STOCK" && (
            <PaymentPlanCard key={`${v.status}-${admin.salePriceCents}`} vehicleId={v.id} onChanged={reload}
              onUndone={(d) => setData(d)} />
          )}
        </div>

        <Card title="Hasar durumu">
          <DamageEditor
            key={JSON.stringify(v.parts)}
            vehicleId={v.id}
            parts={v.parts}
            editable={!!admin}
            onSaved={setData}
          />
        </Card>
      </div>

      {admin && <VehicleForm open={form === "edit"} onClose={() => setForm(null)} existing={admin}
        onSaved={(d) => { setData(d); setForm(null); }} />}
      {admin && <SaleForm open={form === "sell"} onClose={() => setForm(null)} vehicle={admin}
        onSaved={(d) => { setData(d); setForm(null); }} />}
    </div>
  );
}

// Maliyet = gercek alis + araca bagli masraflar (defterden). Kar sadece satilmis aracta.
// Konsinye: alis yok; kar = komisyon - galerinin ustlendigi masraflar, sahibine borc ve yansitilan masraf ayri.
// Ortakli: kar, ortaklarin payina gore bolunur (sadece gosterim).
function CostCard({ vehicle, ownerName }: { vehicle: VehicleDetail; ownerName?: string }) {
  const { data, error, reload } = useLoad(() => api.vehicleCost(vehicle.id), [vehicle.id]);
  const [expenseOpen, setExpenseOpen] = useState(false);
  const expenses = (data?.entries ?? []).filter((e) => e.type === "VEHICLE_EXPENSE");
  const consignment = data?.ownership === "CONSIGNMENT";

  return (
    <Card title={consignment ? "Komisyon ve masraflar" : "Maliyet"}>
      <ErrorBox message={error} />
      {data && consignment && (
        <>
          <dl className="grid grid-cols-2 sm:grid-cols-3 gap-x-4 gap-y-3">
            <Info label="Komisyon" value={data.commissionCents == null ? "Satışta belli olur" : tl(data.commissionCents)}
              numeric={data.commissionCents != null} strong />
            <Info label="Galerinin masrafı" value={tl(data.expenseCents)} numeric />
            <Info label="Sahibine yansıtılan" value={tl(data.ownerChargedCents)} numeric />
            {data.profitCents != null && <>
              <Info label="Satış bedeli" value={tl(data.salePriceCents)} numeric />
              <ProfitInfo cents={data.profitCents} label="Galerinin kârı" />
            </>}
          </dl>
          {data.ownerPayoutCents != null && (
            <CollectedBar title={`${ownerName ?? "Sahibi"} · satıştan alacağı`}
              total={data.ownerPayoutCents} collected={data.ownerCollectedCents ?? 0}
              note="Borç yazılmaz: tahsil edildikçe sahibinin sermayesine geçer. Komisyon galerinin kârıdır." />
          )}
        </>
      )}
      {data && !consignment && (
        <>
          <dl className="grid grid-cols-2 sm:grid-cols-3 gap-x-4 gap-y-3">
            <div>
              <dt className="text-[13px] text-murekkep-2">Alış</dt>
              <dd className="font-cond text-[19px] font-medium">{tl(data.purchaseCents)}</dd>
              {!data.purchaseInLedger && data.purchaseCents > 0 && <dd className="text-[13px] text-uyari">Ödemesi kasaya işlenmedi</dd>}
            </div>
            <Info label="Masraflar" value={tl(data.expenseCents)} numeric />
            <Info label="Toplam maliyet" value={tl(data.totalCents)} numeric strong />
            {data.profitCents != null && <>
              <Info label="Satış bedeli" value={tl(data.salePriceCents)} numeric />
              <ProfitInfo cents={data.profitCents} />
            </>}
          </dl>
          {data.shares.length > 0 && <SharesTable shares={data.shares} sold={data.profitCents != null} />}
        </>
      )}
      {data && (
        <>
          <div className="mt-4 pt-3 border-t border-cizgi">
            <div className="flex items-center justify-between mb-2">
              <h3 className="font-semibold text-[15px] text-murekkep-2">Masraf dökümü</h3>
              <Button variant="secondary" className="h-9" onClick={() => setExpenseOpen(true)}>Masraf ekle</Button>
            </div>
            {expenses.length === 0
              ? <p className="text-[14px] text-murekkep-3">Bu araca henüz masraf girilmedi.</p>
              : <LedgerEntryList entries={expenses} direction={() => 0} onChange={reload} showVehicle={false} />}
          </div>
        </>
      )}
      {expenseOpen && <BulkExpenseForm vehicle={vehicle} onClose={() => setExpenseOpen(false)}
        onSaved={() => { setExpenseOpen(false); reload(); }} />}
    </Card>
  );
}

// Ortakli aracin sahipleri: sahiplik (koydugu para) ve kar payi orani her zaman; satildiysa her tahsilatin kar payi
// oraniyla bolundugu, sahibin alacagi, su ana kadar ona dagitilan ve bekleyen.
function SharesTable({ shares, sold }: { shares: ShareResponse[]; sold: boolean }) {
  return (
    <div className="mt-4 pt-3 border-t border-cizgi">
      <h3 className="etiket mb-1">{sold ? "Satış parasının dağıtımı" : "Ortak payları"}</h3>
      {sold && <p className="text-[13px] text-murekkep-2 mb-3">Her tahsilat kâr payı oranıyla bölünür; ortağın masraf payı ilk tahsilattan düşer.</p>}
      <div className="overflow-x-auto -mx-1 px-1">
        <table className="w-full text-[14px] min-w-[420px]">
          <thead>
            <tr className="text-left text-murekkep-2 text-[12px]">
              <th className="font-semibold py-1.5 pr-2">Sahip</th>
              <th className="font-semibold py-1.5 px-2 text-right">Sahiplik</th>
              <th className="font-semibold py-1.5 px-2 text-right">Kâr payı</th>
              <th className="font-semibold py-1.5 px-2 text-right">Koyduğu</th>
              {sold && <th className="font-semibold py-1.5 px-2 text-right">Kâr</th>}
              {sold && <th className="font-semibold py-1.5 pl-2 text-right">Tahsil / alacak</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-cizgi">
            {shares.map((s) => (
              <tr key={s.userId}>
                <td className="py-2 pr-2 font-semibold">
                  <span className="block truncate max-w-[160px]">{s.name}</span>
                  {s.owner && <span className="text-[11px] font-semibold text-plaka-koyu">Dükkân sahibi</span>}
                </td>
                <td className="py-2 px-2 text-right sayi">{percent(s.shareBp)}</td>
                <td className="py-2 px-2 text-right sayi font-bold text-ortakli">{percent(s.profitShareBp)}</td>
                <td className="py-2 px-2 text-right sayi whitespace-nowrap">{tl(s.contributionCents)}</td>
                {sold && (
                  <td className={`py-2 px-2 text-right sayi font-semibold whitespace-nowrap ${(s.profitCents ?? 0) >= 0 ? "text-basari" : "text-tehlike"}`}>
                    {(s.profitCents ?? 0) < 0 ? "−" : ""}{tl(Math.abs(s.profitCents ?? 0))}
                  </td>
                )}
                {sold && (
                  <td className="py-2 pl-2 text-right sayi whitespace-nowrap">
                    {s.owner || s.collectedCents == null ? <span className="text-murekkep-3">kasada</span>
                      : <>{tl(s.collectedCents)}<span className="text-murekkep-3"> / {tl(s.receivableCents)}</span></>}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// Satistan alacagin ne kadari tahsil edilip sahibine gecti
function CollectedBar({ title, total, collected, note }: { title: string; total: number; collected: number; note: string }) {
  const pct = total > 0 ? Math.min(100, Math.round((collected * 100) / total)) : 0;
  return (
    <div className="mt-4 pt-3 border-t border-cizgi">
      <div className="flex items-baseline justify-between gap-3">
        <h3 className="etiket">{title}</h3>
        <span className="sayi font-bold text-[17px]">{tl(total)}</span>
      </div>
      <div className="mt-2 h-2.5 rounded-full bg-yuzey-2 overflow-hidden">
        <div className="h-full bg-basari rounded-full" style={{ width: `${pct}%` }} />
      </div>
      <div className="mt-1.5 flex justify-between text-[13px]">
        <span className="text-basari font-semibold">Sermayesine geçen {tl(collected)}</span>
        <span className="text-murekkep-2">Bekleyen {tl(total - collected)}</span>
      </div>
      <p className="text-[12px] text-murekkep-3 mt-1.5">{note}</p>
    </div>
  );
}

function DamageEditor({ vehicleId, parts, editable, onSaved }: {
  vehicleId: string; parts: Record<BodyPart, PartCondition>; editable: boolean; onSaved: (d: VehicleDetailAdmin) => void;
}) {
  const [draft, setDraft] = useState(parts);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const changed = (Object.keys(parts) as BodyPart[]).filter((p) => parts[p] !== draft[p]);

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      onSaved(await api.updateParts(vehicleId, Object.fromEntries(changed.map((p) => [p, draft[p]]))));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <DamageMap parts={draft} editable={editable} onChange={(p, c) => setDraft((d) => ({ ...d, [p]: c }))} />
      {changed.length > 0 && (
        <div className="mt-4 flex items-center justify-between gap-2 rounded-lg bg-plaka-acik px-3 py-2">
          <span className="text-[14px] font-semibold text-plaka-koyu">{changed.length} parça değişti</span>
          <div className="flex gap-2">
            <Button variant="quiet" className="h-9" onClick={() => setDraft(parts)}>Geri al</Button>
            <Button className="h-9" onClick={save} disabled={saving}>Kaydet</Button>
          </div>
        </div>
      )}
      <div className="mt-2"><ErrorBox message={error} /></div>
    </div>
  );
}

function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="kart p-4 md:p-5">
      <h2 className="font-cond font-bold text-[21px] tracking-tight pb-3 mb-4 border-b border-cizgi">{title}</h2>
      {children}
    </section>
  );
}

function ProfitInfo({ cents, label }: { cents: number; label?: string }) {
  return (
    <div className="min-w-0">
      <dt className="etiket">{cents >= 0 ? label ?? "Kâr" : "Zarar"}</dt>
      <dd className={`mt-1 sayi text-[21px] leading-tight font-bold ${cents >= 0 ? "text-basari" : "text-tehlike"}`}>{tl(Math.abs(cents))}</dd>
    </div>
  );
}

function ContactInfo({ label, contact, link = true }: { label: string; contact: ContactSummary | null; link?: boolean }) {
  return (
    <div className="min-w-0">
      <dt className="etiket">{label}</dt>
      <dd className="mt-1 truncate text-[16px] font-semibold">
        {contact ? (link ? <a href={`#/contacts/${contact.id}`} className="text-konsinye hover:underline">{contact.name}</a>
          : <span className="text-konsinye">{contact.name}</span>) : "—"}
      </dd>
    </div>
  );
}

function Info({ label, value, numeric = false, strong = false }: { label: string; value: string; numeric?: boolean; strong?: boolean }) {
  return (
    <div className="min-w-0">
      <dt className="etiket">{label}</dt>
      <dd className={`mt-1 truncate ${numeric ? "sayi text-[21px] leading-tight" : "text-[16px] text-murekkep"} ${strong ? "font-bold" : numeric ? "font-medium" : "font-semibold"}`}>{value}</dd>
    </div>
  );
}
