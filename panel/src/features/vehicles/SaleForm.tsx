// Satis: alici, bedel ve odeme plani (PlanBuilder). Altta her zaman "Planlanan / Kalan"; kalan 0 olmadan kaydedilmez.
// Pesinat ("simdi alindi") ayni islemde kasaya girer. Ortak aracta altta ortaga donus secimi (OwnerReturns).
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { tlToCents, todayIso } from "../../shared/format";
import type { ContactResponse, ContactSummary, VehicleDetailAdmin } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal } from "../../shared/ui/Common";
import { ContactPicker } from "../contacts/ContactPicker";
import { cashPlan, rowProblem, toRequest, type PlanMode, type PlanRow } from "../payments/plan";
import { PlanBuilder, PlanSummary } from "../payments/PlanBuilder";
import { NumberInput } from "../../shared/ui/NumberInput";
import { OwnerReturns } from "./OwnerReturns";
import { toOwnerReturns, type OwnerReturnDraft } from "./ownerReturnDraft";
import { ownerReceivables } from "./coOwners";

interface Props { open: boolean; onClose: () => void; vehicle: VehicleDetailAdmin; onSaved: (d: VehicleDetailAdmin) => void }

export function SaleForm(props: Props) {
  return props.open ? <SaleFormContent {...props} /> : null;
}

function SaleFormContent({ onClose, vehicle, onSaved }: Props) {
  const [date, setDate] = useState(todayIso());
  const [buyer, setBuyer] = useState<ContactSummary | null>(null);
  const [soldBy, setSoldBy] = useState("");
  const [priceText, setPriceText] = useState("");
  const [mode, setMode] = useState<PlanMode | null>(null);
  const [rows, setRows] = useState<PlanRow[]>([]);
  const [returns, setReturns] = useState<Record<string, OwnerReturnDraft>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const contacts = useLoad(() => api.contacts(), []);
  const staff = useLoad(() => api.staff(), []);
  const accounts = useLoad(() => api.accounts(), []);
  const coOwned = vehicle.vehicle.ownership === "CO_OWNED";
  // Ortak arac: tahmini kar icin maliyet (alis + masraflar)
  const cost = useLoad(() => (coOwned ? api.vehicleCost(vehicle.vehicle.id) : Promise.resolve(null)), [coOwned, vehicle.vehicle.id]);
  const [extraContacts, setExtraContacts] = useState<ContactResponse[]>([]);
  const allContacts = [...extraContacts, ...(contacts.data ?? [])];

  const parsed = tlToCents(priceText);
  const price = parsed != null && !Number.isNaN(parsed) ? parsed : 0;
  const planned = rows.reduce((t, r) => t + (r.amountCents > 0 ? r.amountCents : 0), 0);
  const remaining = price - planned;
  const canSave = !!buyer && price > 0 && rows.length > 0 && remaining === 0 && rows.every((r) => rowProblem(r) == null);

  const changePrice = (text: string) => {
    setPriceText(text);
    // Pesin planda bedel degisince tek satir da degissin (elle duzenlenen planlara dokunulmaz)
    const cents = tlToCents(text);
    if (mode === "CASH" && cents != null && !Number.isNaN(cents)) {
      setRows(cashPlan(cents, date, rows[0]?.accountId || accounts.data?.[0]?.id || ""));
    }
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!buyer) return setError("Alıcıyı seç.");
    if (!canSave) return setError("Planı tamamla: kalan 0 olmalı ve her satır dolu olmalı.");
    const profit = cost.data ? price - cost.data.totalCents : null;
    const receivables = profit == null ? {} : ownerReceivables(profit, vehicle.shares,
      vehicle.shopCutType, vehicle.shopCutRateBp, vehicle.shopCutCents);
    const ownerReturns = toOwnerReturns(returns, receivables);
    if (typeof ownerReturns === "string") return setError(ownerReturns);
    setSaving(true);
    setError(null);
    try {
      onSaved(await api.sell(vehicle.vehicle.id, {
        saleDate: date, buyerId: buyer.id, soldById: soldBy || null, salePriceCents: price, installments: rows.map(toRequest),
        ownerReturns: coOwned ? ownerReturns : null,
      }));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="Satışı kaydet" open onClose={onClose} wide
      footer={
        <div className="w-full flex flex-wrap items-center justify-between gap-3">
          <PlanSummary planned={planned} remaining={remaining} ready={price > 0} />
          <div className="flex gap-2">
            <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
            <Button type="submit" form="sale-form" disabled={saving || !canSave}>{saving ? "Kaydediliyor…" : "Satışı kaydet"}</Button>
          </div>
        </div>
      }>
      <form id="sale-form" onSubmit={submit} className="space-y-5">
        <ErrorBox message={error} />

        <section className="grid gap-3 sm:grid-cols-2">
          <Field label="Alıcı">
            <ContactPicker contacts={allContacts} selected={buyer} onSelect={setBuyer}
              onCreated={(c) => setExtraContacts((l) => [c, ...l])} placeholder="İsim yaz ya da seç" />
          </Field>
          <Field label="Satış bedeli">
            <NumberInput className={`${inputClass} sayi text-[19px] font-semibold`} value={priceText} onValueChange={changePrice}
              placeholder="1.400.000" />
          </Field>
          <Field label="Satış tarihi">
            <input type="date" className={inputClass} value={date} min={vehicle.vehicle.purchaseDate} onChange={(e) => setDate(e.target.value)} required />
          </Field>
          <Field label="Satan çalışan">
            <select className={inputClass} value={soldBy} onChange={(e) => setSoldBy(e.target.value)}>
              <option value="">Seçilmedi</option>
              {(staff.data ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
        </section>

        <section className="space-y-3">
          <p className="etiket">Ödeme planı</p>
          <PlanBuilder baseCents={price} date={date} mode={mode} onMode={setMode} rows={rows} onRows={setRows} remaining={remaining}
            accounts={accounts.data ?? []} organizations={allContacts.filter((c) => c.type === "ORGANIZATION")}
            modes={["CASH", "INSTALLMENTS", "INSTITUTION", "MIXED"]} methods={["CASH", "TRANSFER", "PROMISSORY_NOTE", "CHEQUE", "INSTITUTION"]}
            paidNowLabel="Şimdi alındı" downHint="Şimdi alınır" />
        </section>

        {coOwned && (
          <OwnerReturns owners={vehicle.shares} profitCents={price > 0 && cost.data ? price - cost.data.totalCents : null}
            shopCutType={vehicle.shopCutType} shopCutRateBp={vehicle.shopCutRateBp} shopCutCents={vehicle.shopCutCents}
            accounts={accounts.data ?? []} value={returns} onChange={setReturns} />
        )}
      </form>
    </Modal>
  );
}
