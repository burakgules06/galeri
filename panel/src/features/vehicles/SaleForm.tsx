// Satis: alici, bedel ve odeme plani (PlanBuilder). Altta her zaman "Planlanan / Kalan"; kalan 0 olmadan kaydedilmez.
// Pesinat ("simdi alindi") ayni islemde kasaya girer. Ortak aracta altta ortaga donus secimi (OwnerReturns).
// Toplu satis: "+ Araç ekle" ile ayni aliciya stoktaki baska araclar eklenir; bedeller toplanir, plan toplam uzerinden
// kurulur ve tek istekte kaydedilir (sepet birlikte geri alinir).
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { formatDate, tl, tlToCents, todayIso } from "../../shared/format";
import type { ContactResponse, ContactSummary, VehicleDetailAdmin, VehicleSummary } from "../../shared/types";
import { Plate } from "../../shared/ui/Plate";
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
  const holders = useLoad(() => api.holders(), []);
  const capitalBuyer = !!buyer && (holders.data ?? []).some((h) => h.contactId === buyer.id);
  const planRows = capitalBuyer ? rows : rows.map((r) => r.capitalCents === 0 ? r : { ...r, capitalCents: 0 });
  const coOwned = vehicle.vehicle.ownership === "CO_OWNED";
  // Ortak arac: tahmini kar icin maliyet (alis + masraflar)
  const cost = useLoad(() => (coOwned ? api.vehicleCost(vehicle.vehicle.id) : Promise.resolve(null)), [coOwned, vehicle.vehicle.id]);
  const [extraContacts, setExtraContacts] = useState<ContactResponse[]>([]);
  const allContacts = [...extraContacts, ...(contacts.data ?? [])];
  // Sepet: bu araca ek olarak satilan araclar ve bedelleri
  const [basket, setBasket] = useState<{ v: VehicleSummary; priceText: string }[]>([]);
  const [picking, setPicking] = useState(false);
  const stock = useLoad(() => (picking ? api.vehicles("", "IN_STOCK") : Promise.resolve([] as VehicleSummary[])), [picking]);

  const parsed = tlToCents(priceText);
  const ownPrice = parsed != null && !Number.isNaN(parsed) ? parsed : 0;
  const centsOf = (t: string) => { const c = tlToCents(t); return c != null && !Number.isNaN(c) ? c : 0; };
  const price = ownPrice + basket.reduce((t, b) => t + centsOf(b.priceText), 0);
  const planned = planRows.reduce((t, r) => t + (r.amountCents > 0 ? r.amountCents : 0), 0);
  const remaining = price - planned;
  const beforePurchase = !!vehicle.vehicle.purchaseDate && date < vehicle.vehicle.purchaseDate;
  const canSave = !beforePurchase && !!buyer && ownPrice > 0 && basket.every((b) => centsOf(b.priceText) > 0) && price > 0 && planRows.length > 0 && remaining === 0 && planRows.every((r) => rowProblem(r, capitalBuyer) == null);

  // Pesin planda toplam degisince tek satir da degissin (elle duzenlenen planlara dokunulmaz)
  const syncCash = (total: number) => {
    if (mode === "CASH") setRows(cashPlan(total, date, rows[0]?.accountId || accounts.data?.[0]?.id || ""));
  };
  const changePrice = (text: string) => {
    setPriceText(text);
    syncCash(centsOf(text) + basket.reduce((t, b) => t + centsOf(b.priceText), 0));
  };
  const changeBasket = (next: { v: VehicleSummary; priceText: string }[]) => {
    setBasket(next);
    syncCash(ownPrice + next.reduce((t, b) => t + centsOf(b.priceText), 0));
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
      if (basket.length > 0) {
        onSaved(await api.sellBasket({
          saleDate: date, buyerId: buyer.id, soldById: soldBy || null, installments: planRows.map(toRequest),
          items: [{ vehicleId: vehicle.vehicle.id, salePriceCents: ownPrice }, ...basket.map((b) => ({ vehicleId: b.v.id, salePriceCents: centsOf(b.priceText) }))],
        }));
        return;
      }
      onSaved(await api.sell(vehicle.vehicle.id, {
        saleDate: date, buyerId: buyer.id, soldById: soldBy || null, salePriceCents: price, installments: planRows.map(toRequest),
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
          <Field label={basket.length ? `${vehicle.vehicle.plate} bedeli` : "Satış bedeli"}>
            <NumberInput className={`${inputClass} sayi text-[19px] font-semibold`} value={priceText} onValueChange={changePrice}
              placeholder="1.400.000" />
          </Field>
          <Field label="Satış tarihi">
            <input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} required />
            {beforePurchase && (
              <p className="text-[13px] text-tehlike mt-1">
                Aracın alış tarihi {formatDate(vehicle.vehicle.purchaseDate)}. Satış bundan önce olamaz; önce araçta alış tarihini düzelt.
              </p>
            )}
          </Field>
          <Field label="Satan çalışan">
            <select className={inputClass} value={soldBy} onChange={(e) => setSoldBy(e.target.value)}>
              <option value="">Seçilmedi</option>
              {(staff.data ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
        </section>

        <section className="space-y-2">
          {basket.map((b, k) => (
            <div key={b.v.id} className="grid grid-cols-[1fr_130px_36px] md:grid-cols-[1fr_170px_36px] gap-2 items-center">
              <span className="flex items-center gap-2 min-w-0"><Plate plate={b.v.plate} size="sm" /><span className="truncate">{b.v.make} {b.v.model}</span></span>
              <NumberInput className={`${inputClass} sayi`} value={b.priceText} aria-label={`${b.v.plate} bedeli`} placeholder="Bedel" preview={false}
                onValueChange={(t) => changeBasket(basket.map((x, j) => (j === k ? { ...x, priceText: t } : x)))} />
              <button type="button" aria-label="Araçtan çıkar" onClick={() => changeBasket(basket.filter((_, j) => j !== k))}
                className="basilir w-9 h-9 rounded-lg hover:bg-yuzey-2 grid place-items-center text-murekkep-3">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
              </button>
            </div>
          ))}
          {picking ? (
            <select className={inputClass} autoFocus defaultValue="" aria-label="Eklenecek araç" onChange={(e) => {
              const v = (stock.data ?? []).find((x) => x.id === e.target.value);
              if (v) changeBasket([...basket, { v, priceText: "" }]);
              setPicking(false);
            }}>
              <option value="" disabled>{stock.data ? "Araç seç" : "Yükleniyor…"}</option>
              {(stock.data ?? []).filter((x) => x.id !== vehicle.vehicle.id && !basket.some((b) => b.v.id === x.id))
                .map((x) => <option key={x.id} value={x.id}>{x.plate} · {x.make} {x.model}</option>)}
            </select>
          ) : (
            <Button type="button" variant="quiet" className="text-plaka" onClick={() => setPicking(true)}>+ Aynı alıcıya araç ekle</Button>
          )}
          {basket.length > 0 && <p className="text-[14px]">Toplam <b className="sayi text-[17px]">{tl(price)}</b> · {basket.length + 1} araç</p>}
        </section>

        <section className="space-y-3">
          <p className="etiket">Ödeme planı</p>
          <PlanBuilder baseCents={price} date={date} mode={mode} onMode={setMode} rows={planRows} onRows={setRows} remaining={remaining}
            accounts={accounts.data ?? []} organizations={allContacts.filter((c) => c.type === "ORGANIZATION")}
            modes={["CASH", "INSTALLMENTS", "CHEQUE", "INSTITUTION", "MIXED"]} methods={["CASH", "TRANSFER", "PROMISSORY_NOTE", "CHEQUE", "INSTITUTION"]}
            paidNowLabel="Şimdi alındı" downHint="Şimdi alınır" capitalBuyer={capitalBuyer} />
        </section>

        {coOwned && basket.length === 0 && (
          <OwnerReturns owners={vehicle.shares} profitCents={price > 0 && cost.data ? price - cost.data.totalCents : null}
            shopCutType={vehicle.shopCutType} shopCutRateBp={vehicle.shopCutRateBp} shopCutCents={vehicle.shopCutCents}
            accounts={accounts.data ?? []} value={returns} onChange={setReturns} />
        )}
      </form>
    </Modal>
  );
}
