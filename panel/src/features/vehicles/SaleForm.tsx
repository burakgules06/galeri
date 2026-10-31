// Satis: solda alici, bedel ve odeme plani (PlanBuilder: Nakit, Senet, Cek, Acik hesap, Evim / kredi, Kredi karti, Karma);
// sagda yapiskan ozet (bedel, simdi alinan, vadeli, kalan; ortakli / konsinyede paranin kime gectigi). Kalan 0 olmadan
// kaydedilmez. Pesinat ("simdi alindi") ayni islemde kasaya / bankaya girer.
// Toplu satis: "+ Aynı alıcıya araç ekle" ile stoktaki baska araclar eklenir; bedeller toplanir, plan toplam uzerinden kurulur.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { formatDate, percent, tl, tlToCents, todayIso } from "../../shared/format";
import type { ContactResponse, ContactSummary, VehicleDetailAdmin, VehicleSummary } from "../../shared/types";
import { Plate } from "../../shared/ui/Plate";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal } from "../../shared/ui/Common";
import { ContactPicker } from "../contacts/ContactPicker";
import { rowProblem, toRequest, usePlan } from "../payments/plan";
import { PlanBuilder } from "../payments/PlanBuilder";
import { NumberInput } from "../../shared/ui/NumberInput";
import { OwnerReturns } from "./OwnerReturns";
import { toOwnerReturns, type OwnerReturnDraft } from "./ownerReturnDraft";
import { ownerSaleShares } from "./coOwners";

interface Props { open: boolean; onClose: () => void; vehicle: VehicleDetailAdmin; onSaved: (d: VehicleDetailAdmin) => void }

export function SaleForm(props: Props) {
  return props.open ? <SaleFormContent {...props} /> : null;
}

const centsOf = (t: string) => { const c = tlToCents(t); return c != null && !Number.isNaN(c) ? c : 0; };

function SaleFormContent({ onClose, vehicle, onSaved }: Props) {
  const [date, setDate] = useState(todayIso());
  const [buyer, setBuyer] = useState<ContactSummary | null>(null);
  const [soldBy, setSoldBy] = useState("");
  const [priceText, setPriceText] = useState("");
  const [returns, setReturns] = useState<Record<string, OwnerReturnDraft>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const contacts = useLoad(() => api.contacts(), []);
  const staff = useLoad(() => api.staff(), []);
  const accounts = useLoad(() => api.accounts(), []);
  const coOwned = vehicle.vehicle.ownership === "CO_OWNED";
  const consignment = vehicle.vehicle.ownership === "CONSIGNMENT";
  // Ortak arac: tahmin icin masraflar
  const cost = useLoad(() => (coOwned ? api.vehicleCost(vehicle.vehicle.id) : Promise.resolve(null)), [coOwned, vehicle.vehicle.id]);
  const [extraContacts, setExtraContacts] = useState<ContactResponse[]>([]);
  const allContacts = [...extraContacts, ...(contacts.data ?? [])];
  // Sepet: bu araca ek olarak satilan araclar ve bedelleri
  const [basket, setBasket] = useState<{ v: VehicleSummary; priceText: string }[]>([]);
  const [picking, setPicking] = useState(false);
  const stock = useLoad(() => (picking ? api.vehicles("", "IN_STOCK") : Promise.resolve([] as VehicleSummary[])), [picking]);

  const ownPrice = centsOf(priceText);
  const price = ownPrice + basket.reduce((t, b) => t + centsOf(b.priceText), 0);
  const cashAccount = (accounts.data?.find((a) => a.type === "CASH") ?? accounts.data?.[0])?.id ?? "";
  const plan = usePlan({ priceCents: price, date, defaultAccount: cashAccount });
  const rows = plan.rows;
  const paidNowCents = rows.reduce((t, r) => t + (r.paidNow && r.amountCents > 0 ? r.amountCents : 0), 0);
  const planned = rows.reduce((t, r) => t + (r.amountCents > 0 ? r.amountCents : 0), 0);
  const remaining = price - planned;
  const beforePurchase = !!vehicle.vehicle.purchaseDate && date < vehicle.vehicle.purchaseDate;
  const canSave = !beforePurchase && !!buyer && ownPrice > 0 && basket.every((b) => centsOf(b.priceText) > 0) && price > 0
    && rows.length > 0 && remaining === 0 && rows.every((r) => rowProblem(r) == null);
  const blocker = !buyer ? "Alıcıyı seç" : price <= 0 ? "Bedeli yaz" : plan.mode == null ? "Ödeme şeklini seç"
    : plan.problem ?? (rows.some((r) => rowProblem(r)) ? "Plandaki satırları tamamla" : remaining !== 0 ? "Kalan 0 olmalı" : null);

  const shares = coOwned && basket.length === 0
    ? ownerSaleShares(price, paidNowCents, vehicle.actualPurchaseCents ?? 0, cost.data?.expenseCents ?? 0, vehicle.shares,
      vehicle.shopCutType, vehicle.shopCutRateBp, vehicle.shopCutCents)
    : {};
  const commission = consignment ? (vehicle.commissionType === "FIXED" ? vehicle.commissionCents ?? 0
    : Math.round((price * (vehicle.commissionRateBp ?? 0)) / 10000)) : 0;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSave) return setError(blocker ?? "Formu tamamla.");
    const receivables = Object.fromEntries(Object.entries(shares).map(([id, s]) => [id, s.now]));
    const ownerReturns = toOwnerReturns(returns, receivables);
    if (typeof ownerReturns === "string") return setError(ownerReturns);
    setSaving(true);
    setError(null);
    try {
      if (basket.length > 0) {
        onSaved(await api.sellBasket({
          saleDate: date, buyerId: buyer!.id, soldById: soldBy || null, installments: rows.map(toRequest),
          items: [{ vehicleId: vehicle.vehicle.id, salePriceCents: ownPrice }, ...basket.map((b) => ({ vehicleId: b.v.id, salePriceCents: centsOf(b.priceText) }))],
        }));
        return;
      }
      onSaved(await api.sell(vehicle.vehicle.id, {
        saleDate: date, buyerId: buyer!.id, soldById: soldBy || null, salePriceCents: price, installments: rows.map(toRequest),
        ownerReturns: coOwned ? ownerReturns : null,
      }));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="Satışı kaydet" open onClose={onClose} size="xl"
      footer={
        <div className="w-full flex flex-wrap items-center justify-between gap-3">
          <p className={`text-[14px] min-h-5 ${blocker ? "text-murekkep-2" : "text-basari font-semibold"}`}>{blocker ?? "Kaydetmeye hazır"}</p>
          <div className="flex gap-2">
            <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
            <Button type="submit" form="sale-form" disabled={saving || !canSave}>{saving ? "Kaydediliyor…" : "Satışı kaydet"}</Button>
          </div>
        </div>
      }>
      <form id="sale-form" onSubmit={submit} className="grid gap-6 md:grid-cols-[minmax(0,1fr)_300px] md:items-start">
        <div className="space-y-6 min-w-0">
          <ErrorBox message={error} />
          <div className="flex items-center gap-3 min-w-0">
            <Plate plate={vehicle.vehicle.plate} />
            <span className="font-semibold truncate">{vehicle.vehicle.make} <span className="font-medium text-murekkep-2">{vehicle.vehicle.model}</span></span>
          </div>

          <section className="grid gap-x-4 gap-y-3 sm:grid-cols-2">
            <Field label="Alıcı">
              <ContactPicker contacts={allContacts} selected={buyer} onSelect={setBuyer}
                onCreated={(c) => setExtraContacts((l) => [c, ...l])} placeholder="İsim yaz ya da seç" />
            </Field>
            <Field label={basket.length ? `${vehicle.vehicle.plate} bedeli` : "Satış bedeli"}>
              <NumberInput className={`${inputClass} sayi text-[19px] font-semibold`} value={priceText} onValueChange={setPriceText}
                placeholder="1.400.000" />
            </Field>
            <Field label="Satış tarihi">
              <input type="date" className={`${inputClass} ${beforePurchase ? "border-tehlike" : ""}`} value={date} onChange={(e) => setDate(e.target.value)} required />
            </Field>
            <Field label="Satan çalışan">
              <select className={inputClass} value={soldBy} onChange={(e) => setSoldBy(e.target.value)}>
                <option value="">Seçilmedi</option>
                {(staff.data ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </Field>
            {beforePurchase && (
              <p className="sm:col-span-2 text-[13px] text-tehlike -mt-1">
                Aracın alış tarihi {formatDate(vehicle.vehicle.purchaseDate)}. Satış bundan önce olamaz; önce araçta alış tarihini düzelt.
              </p>
            )}
          </section>

          <section className="space-y-2">
            {basket.map((b, k) => (
              <div key={b.v.id} className="grid grid-cols-[1fr_130px_36px] md:grid-cols-[1fr_170px_36px] gap-2 items-center">
                <span className="flex items-center gap-2 min-w-0"><Plate plate={b.v.plate} size="sm" /><span className="truncate">{b.v.make} {b.v.model}</span></span>
                <NumberInput className={`${inputClass} sayi`} value={b.priceText} aria-label={`${b.v.plate} bedeli`} placeholder="Bedel" preview={false}
                  onValueChange={(t) => setBasket(basket.map((x, j) => (j === k ? { ...x, priceText: t } : x)))} />
                <button type="button" aria-label="Araçtan çıkar" onClick={() => setBasket(basket.filter((_, j) => j !== k))}
                  className="basilir w-9 h-9 rounded-lg hover:bg-yuzey-2 grid place-items-center text-murekkep-3">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
                </button>
              </div>
            ))}
            {picking ? (
              <select className={inputClass} autoFocus defaultValue="" aria-label="Eklenecek araç" onChange={(e) => {
                const v = (stock.data ?? []).find((x) => x.id === e.target.value);
                if (v) setBasket([...basket, { v, priceText: "" }]);
                setPicking(false);
              }}>
                <option value="" disabled>{stock.data ? "Araç seç" : "Yükleniyor…"}</option>
                {(stock.data ?? []).filter((x) => x.id !== vehicle.vehicle.id && !basket.some((b) => b.v.id === x.id))
                  .map((x) => <option key={x.id} value={x.id}>{x.plate} · {x.make} {x.model}</option>)}
              </select>
            ) : (
              <Button type="button" variant="quiet" className="text-plaka -ml-2" onClick={() => setPicking(true)}>+ Aynı alıcıya araç ekle</Button>
            )}
          </section>

          <section className="space-y-3">
            <h3 className="font-cond font-semibold text-[18px]">Ödeme şekli</h3>
            <PlanBuilder plan={plan} baseCents={price} accounts={accounts.data ?? []}
              organizations={allContacts.filter((c) => c.type === "ORGANIZATION")} onOrgCreated={(c) => setExtraContacts((l) => [c, ...l])}
              modes={["CASH", "PROMISSORY_NOTE", "CHEQUE", "OPEN_ACCOUNT", "INSTITUTION", "CREDIT_CARD", "MIXED"]}
              methods={["CASH", "TRANSFER", "CREDIT_CARD", "PROMISSORY_NOTE", "CHEQUE", "OPEN_ACCOUNT", "INSTITUTION"]}
              nowLabel="Şimdi alındı" downLabel="Peşinat (şimdi alınır)" />
          </section>

          {coOwned && basket.length === 0 && (
            <OwnerReturns owners={vehicle.shares} saleCents={price} paidNowCents={paidNowCents}
              purchaseCents={vehicle.actualPurchaseCents ?? 0} expenseCents={cost.data?.expenseCents ?? 0}
              shopCutType={vehicle.shopCutType} shopCutRateBp={vehicle.shopCutRateBp} shopCutCents={vehicle.shopCutCents}
              accounts={accounts.data ?? []} value={returns} onChange={setReturns} />
          )}
        </div>

        <aside className="md:sticky md:top-0 rounded-[14px] border border-cizgi bg-yuzey-2 p-4 space-y-4">
          <div>
            <p className="etiket">Satış bedeli</p>
            <p className="sayi font-bold text-[28px] leading-tight mt-1">{tl(price)}</p>
            {basket.length > 0 && <p className="text-[13px] text-murekkep-2">{basket.length + 1} araç birlikte</p>}
          </div>
          <dl className="space-y-2 text-[14px]">
            <SummaryLine label="Şimdi alınan" cents={paidNowCents} tone="text-basari" />
            <SummaryLine label="Vadeli" cents={planned - paidNowCents} />
            <div className="border-t border-cizgi pt-2">
              <SummaryLine label="Kalan" cents={remaining} strong
                tone={remaining === 0 && price > 0 ? "text-basari" : remaining < 0 ? "text-tehlike" : "text-uyari"} />
            </div>
          </dl>

          {consignment && (
            <div className="border-t border-cizgi pt-3 space-y-1.5 text-[13px]">
              <p className="etiket">Konsinye</p>
              <SummaryLine label="Komisyon (galeri)" cents={commission} />
              <SummaryLine label={`${vehicle.consignor?.name ?? "Sahibi"} alır`} cents={Math.max(0, price - commission)} />
              <p className="text-murekkep-2 pt-1">Borç yazılmaz. Para seçilen hesaba girer (varsayılan Kasa); sahibinin payı tahsil edildikçe sermayesine geçer.</p>
            </div>
          )}

          {coOwned && basket.length === 0 && vehicle.shares.some((s) => !s.owner) && (
            <div className="border-t border-cizgi pt-3 space-y-2 text-[13px]">
              <p className="etiket">Ortaklara dağıtım</p>
              <ul className="space-y-1.5">
                {vehicle.shares.filter((s) => !s.owner).map((s) => (
                  <li key={s.userId} className="flex items-center gap-2">
                    <span className="grow min-w-0 truncate font-semibold">{s.name}</span>
                    <span className="sayi text-ortakli font-bold">{percent(s.profitShareBp)}</span>
                    <span className="sayi w-24 text-right">{tl(shares[s.userId]?.now ?? 0)}</span>
                  </li>
                ))}
              </ul>
              <p className="text-murekkep-2">Şimdi alınan paradan payları. Kalan tahsil edildikçe kâr payı oranıyla dağıtılır.</p>
            </div>
          )}
        </aside>
      </form>
    </Modal>
  );
}

function SummaryLine({ label, cents, tone = "", strong = false }: { label: string; cents: number; tone?: string; strong?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="text-murekkep-2">{label}</dt>
      <dd className={`sayi whitespace-nowrap ${strong ? "font-bold text-[18px]" : "text-[16px]"} ${tone}`}>{tl(cents)}</dd>
    </div>
  );
}
