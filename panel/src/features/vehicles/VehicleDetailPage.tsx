import { useState, type ReactNode } from "react";
import { api, isAdminDetail } from "../../api/galeri";
import { formatDate, formatNumber, FUEL, STATUS, tl, TRANSMISSION } from "../../shared/format";
import type { BodyPart, PartCondition, VehicleDetail, VehicleDetailAdmin, VehicleStatus } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Plate } from "../../shared/ui/Plate";
import { Button, ErrorBox } from "../../shared/ui/Common";
import { DamageMap } from "./DamageMap";
import { DaysInStock } from "./DaysInStock";
import { PaymentPlanCard } from "./PaymentPlanCard";
import { SaleForm } from "./SaleForm";
import { VehicleForm } from "./VehicleForm";
import { ExpenseForm } from "../ledger/ExpenseForm";
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

  if (error) return <ErrorBox message={error} />;
  if (!data) return <p className="text-murekkep-2 py-10 text-center">Yükleniyor…</p>;

  const admin = isAdminDetail(data) ? data : null;
  const v: VehicleDetail = admin ? admin.vehicle : (data as VehicleDetail);

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
            <DaysInStock days={v.daysInStock} status={v.status} />
          </div>
        </div>
        {admin && (
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setForm("edit")}>Düzenle</Button>
            {v.status === "IN_STOCK" && <Button onClick={() => setForm("sell")}>Satıldı olarak işaretle</Button>}
          </div>
        )}
      </header>

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
                <Info label="Kimden alındı" value={admin.seller?.name ?? "—"} />
                <Info label="Alan çalışan" value={admin.boughtBy?.name ?? "—"} />
                <Info label="Gerçek alış bedeli" value={tl(admin.actualPurchaseCents)} numeric strong />
                <Info label="Resmi alış bedeli" value={tl(admin.officialPurchaseCents)} numeric />
                {v.status !== "IN_STOCK" && <>
                  <Info label="Alıcı" value={admin.buyer?.name ?? "—"} />
                  <Info label="Satan çalışan" value={admin.soldBy?.name ?? "—"} />
                  <Info label="Satış bedeli" value={tl(admin.salePriceCents)} numeric strong />
                </>}
              </dl>
            </Card>
          )}

          {admin && (
            <CostCard key={`${admin.actualPurchaseCents}-${admin.salePriceCents}-${admin.purchaseAccountId}`} vehicle={v} />
          )}

          {admin && (
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
function CostCard({ vehicle }: { vehicle: VehicleDetail }) {
  const { data, error, reload } = useLoad(() => api.vehicleCost(vehicle.id), [vehicle.id]);
  const [expenseOpen, setExpenseOpen] = useState(false);
  const expenses = (data?.entries ?? []).filter((e) => e.type === "VEHICLE_EXPENSE");

  return (
    <Card title="Maliyet">
      <ErrorBox message={error} />
      {data && (
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
              <div>
                <dt className="text-[13px] text-murekkep-2">{data.profitCents >= 0 ? "Kâr" : "Zarar"}</dt>
                <dd className={`font-cond text-[19px] font-bold ${data.profitCents >= 0 ? "text-basari" : "text-tehlike"}`}>{tl(Math.abs(data.profitCents))}</dd>
              </div>
            </>}
          </dl>

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
      <ExpenseForm open={expenseOpen} onClose={() => setExpenseOpen(false)} vehicle={vehicle}
        onSaved={() => { setExpenseOpen(false); reload(); }} />
    </Card>
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

function Info({ label, value, numeric = false, strong = false }: { label: string; value: string; numeric?: boolean; strong?: boolean }) {
  return (
    <div className="min-w-0">
      <dt className="etiket">{label}</dt>
      <dd className={`mt-1 truncate ${numeric ? "sayi text-[21px] leading-tight" : "text-[16px] text-murekkep"} ${strong ? "font-bold" : numeric ? "font-medium" : "font-semibold"}`}>{value}</dd>
    </div>
  );
}
