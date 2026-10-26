// Aracin odeme plani: toplam / odenen / kalan / geciken, ilerleme seridi ve taksitler. Sadece ADMIN.
// SALE: satisin tahsilati, altta "Satisi geri al" (hic taksit odenmemisse; backend de reddeder).
// PURCHASE: senetli / taksitli alisin odenecekleri; plan yoksa kart hic gorunmez (pesin alis).
import { useState } from "react";
import { api } from "../../api/galeri";
import { SplitBar } from "../../shared/charts/Donut";
import { tl } from "../../shared/format";
import type { PlanDirection, VehicleDetailAdmin } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox } from "../../shared/ui/Common";
import { InstallmentRow } from "../payments/InstallmentRow";

export function PaymentPlanCard({ vehicleId, direction = "SALE", onChanged, onUndone }: {
  vehicleId: string;
  direction?: PlanDirection;
  onChanged: () => void;
  onUndone?: (d: VehicleDetailAdmin) => void;
}) {
  const { data, error, reload } = useLoad(() => api.paymentPlans(vehicleId), [vehicleId]);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [undoError, setUndoError] = useState<string | null>(null);
  const plan = data?.find((p) => p.direction === direction);
  const anyPaid = plan?.installments.some((i) => i.status === "PAID") ?? false;
  const sale = direction === "SALE";

  if (!sale && !plan) return null;

  const changed = () => { reload(); onChanged(); };
  const undoSale = async () => {
    setBusy(true);
    setUndoError(null);
    try {
      const d = await api.undoSale(vehicleId);
      onUndone?.(d);
    } catch (e) {
      setUndoError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="kart p-4 md:p-5">
      <div className="flex items-center justify-between gap-3 pb-3 mb-4 border-b border-cizgi">
        <h2 className="font-cond font-bold text-[21px] tracking-tight">{sale ? "Ödeme planı" : "Alış ödeme planı"}</h2>
        {plan && <span className="text-[13px] text-murekkep-2">{sale ? "" : "Satıcı: "}{plan.counterparty.name}</span>}
      </div>
      <ErrorBox message={error} />
      {data && !plan && <p className="text-[14px] text-murekkep-3">Bu satış için ödeme planı yok (eski kayıt).</p>}

      {plan && (
        <>
          <dl className="grid grid-cols-2 sm:grid-cols-4 gap-x-4 gap-y-3">
            <Stat label="Toplam" value={tl(plan.totalCents)} />
            <Stat label={sale ? "Tahsil edilen" : "Ödenen"} value={tl(plan.paidCents)} tone="text-basari" />
            <Stat label="Kalan" value={tl(plan.remainingCents)} tone={plan.remainingCents ? "text-uyari" : "text-murekkep-3"} />
            <Stat label="Geciken" value={tl(plan.overdueCents)} tone={plan.overdueCents ? "text-tehlike" : "text-murekkep-3"} />
          </dl>
          <div className="mt-3">
            <SplitBar height={8} parts={[
              { key: "paid", value: plan.paidCents, color: "var(--color-basari-canli)" },
              { key: "overdue", value: plan.overdueCents, color: "var(--color-tehlike-canli)" },
              { key: "waiting", value: plan.remainingCents - plan.overdueCents, color: "var(--color-boyali)" },
            ]} />
          </div>

          <ul className="mt-3 divide-y divide-cizgi -mx-1">
            {plan.installments.map((i) => (
              <InstallmentRow key={i.id} i={i} onChange={changed} showVehicle={false} showPayer={i.payer.id !== plan.counterparty.id} />
            ))}
          </ul>
        </>
      )}

      {data && sale && (
        <div className="mt-4 pt-3 border-t border-cizgi">
          <ErrorBox message={undoError} />
          {!confirming ? (
            <div className="flex flex-wrap items-center justify-between gap-2">
              {anyPaid && <p className="text-sm text-uyari">Önce tahsilatları geri alın.</p>}
              <Button variant="danger" className="h-9" onClick={() => setConfirming(true)} disabled={anyPaid}>Satışı geri al</Button>
            </div>
          ) : (
            <div className="rounded-[12px] bg-tehlike-zemin border border-tehlike/20 p-3 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <span className="text-[14px] text-tehlike font-semibold">Satış hareketleri terslenecek, plan silinecek, araç stoka dönecek.</span>
              <div className="flex gap-2 shrink-0">
                <Button variant="quiet" className="h-9" onClick={() => setConfirming(false)}>Vazgeç</Button>
                <Button variant="danger" className="h-9" onClick={undoSale} disabled={busy}>Evet, geri al</Button>
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

function Stat({ label, value, tone = "text-murekkep" }: { label: string; value: string; tone?: string }) {
  return (
    <div className="min-w-0">
      <dt className="etiket">{label}</dt>
      <dd className={`mt-1 sayi font-bold text-[20px] leading-tight truncate ${tone}`}>{value}</dd>
    </div>
  );
}
