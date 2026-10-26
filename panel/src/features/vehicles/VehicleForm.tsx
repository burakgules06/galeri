// Arac ekleme / duzenleme. Sadece ADMIN acar. Tutarlar TL girilir, kurus gonderilir.
// Sahiplik: galeri (varsayilan), konsinye (sahibi + komisyon; alis bedeli ve odemesi yok), ortak (Havuz ve/veya ortaklar,
// katki kaynagi, kar payi orani, dukkan payi: CoOwnersEditor).
import { useState, type FormEvent } from "react";
import { CoOwnersEditor } from "./CoOwnersEditor";
import { initialOwners, initialShopCut, ownersProblem, toShareRequests, type OwnerRow, type ShopCut } from "./coOwners";
import { MakeModelPicker } from "./MakeModelPicker";
import type { MakeModelValue } from "./makeModel";
import { api } from "../../api/galeri";
import {
  bpToInput, centsToInput, formatDate, formatNumber, formatPlate, FUEL, METHOD, percentToBp, tl, tlToCents, todayIso, TRANSMISSION,
} from "../../shared/format";
import { NumberInput } from "../../shared/ui/NumberInput";
import type {
  CommissionType, ContactResponse, ContactSummary, Fuel, Ownership, PaymentPlanResponse, Transmission, VehicleDetailAdmin,
  VehicleRequest,
} from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { ContactPicker } from "../contacts/ContactPicker";
import { row, rowProblem, toRequest, type PlanMode, type PlanRow } from "../payments/plan";
import { PlanBuilder, PlanSummary } from "../payments/PlanBuilder";

interface Props {
  open: boolean;
  onClose: () => void;
  onSaved: (d: VehicleDetailAdmin) => void;
  existing?: VehicleDetailAdmin;
}

export function VehicleForm(props: Props) {
  return props.open ? <VehicleFormLoader {...props} /> : null;
}

// Duzenlemede once mevcut alis plani yuklenir: formun baslangic odeme sekli ona gore kurulur.
function VehicleFormLoader(props: Props) {
  const id = props.existing?.vehicle.id;
  const plans = useLoad(() => (id ? api.paymentPlans(id) : Promise.resolve([])), [id]);
  if (!plans.data && !plans.error) return null;
  return <VehicleFormContent {...props} purchasePlan={plans.data?.find((p) => p.direction === "PURCHASE") ?? null} />;
}

function VehicleFormContent({ onClose, onSaved, existing, purchasePlan }: Props & { purchasePlan: PaymentPlanResponse | null }) {
  const v = existing?.vehicle;
  const locked = (purchasePlan?.installments ?? []).filter((i) => i.status === "PAID");
  const lockedCents = locked.reduce((t, i) => t + i.amountCents, 0);
  const [payMode, setPayMode] = useState<"CASH" | "PLAN">(purchasePlan ? "PLAN" : "CASH");
  const [planMode, setPlanMode] = useState<PlanMode | null>(purchasePlan ? "MIXED" : null);
  const [rows, setRows] = useState<PlanRow[]>(() => (purchasePlan?.installments ?? [])
    .filter((i) => i.status !== "PAID")
    .map((i) => row({
      dueDate: i.dueDate, amountCents: i.amountCents, method: i.method, noteNo: i.noteNo ?? "",
      payer: i.payer.id !== i.counterparty.id ? i.payer : null,
    })));
  const [plate, setPlate] = useState(v?.plate ?? "");
  // Katalogdan secilen marka / model; ilk deger sabit kalsin (secici her render'da yeniden eslestirmesin)
  const [initialMakeModel] = useState(() => (v ? { make: v.make, model: v.model } : null));
  const [makeModel, setMakeModel] = useState<MakeModelValue | null>(null);
  const [year, setYear] = useState(v ? String(v.modelYear) : "");
  const [fuel, setFuel] = useState<Fuel>(v?.fuel ?? "GASOLINE");
  const [transmission, setTransmission] = useState<Transmission>(v?.transmission ?? "MANUAL");
  const [mileage, setMileage] = useState(v ? formatNumber(v.mileageKm) : "");
  const [color, setColor] = useState(v?.color ?? "");
  const [tramer, setTramer] = useState(centsToInput(v?.tramerCents));
  const [notes, setNotes] = useState(v?.notes ?? "");
  const [purchaseDate, setPurchaseDate] = useState(v?.purchaseDate ?? todayIso());
  const [seller, setSeller] = useState<ContactSummary | null>(existing?.seller ?? null);
  const [boughtBy, setBoughtBy] = useState(existing?.boughtBy?.id ?? "");
  const [actual, setActual] = useState(centsToInput(existing?.actualPurchaseCents));
  const [official, setOfficial] = useState(centsToInput(existing?.officialPurchaseCents));
  // null: henuz secilmedi, yeni aracta kasa varsayilir. "": deftere yazilmaz.
  const [purchaseAccount, setPurchaseAccount] = useState<string | null>(existing ? (existing.purchaseAccountId ?? "") : null);
  const [ownership, setOwnership] = useState<Ownership>(v?.ownership ?? "DEALER");
  const [consignor, setConsignor] = useState<ContactSummary | null>(existing?.consignor ?? null);
  const [commissionType, setCommissionType] = useState<CommissionType>(existing?.commissionType ?? "RATE");
  const [commissionRate, setCommissionRate] = useState(bpToInput(existing?.commissionRateBp));
  const [commissionFixed, setCommissionFixed] = useState(centsToInput(existing?.commissionCents));
  const [owners, setOwners] = useState<OwnerRow[]>(() => initialOwners(existing));
  const [profitDiffers, setProfitDiffers] = useState(() => (existing?.shares ?? []).some((s) => s.profitShareBp !== s.shareBp));
  const [shopCut, setShopCut] = useState<ShopCut>(() => initialShopCut(existing));
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const consignment = ownership === "CONSIGNMENT";
  const coOwned = ownership === "CO_OWNED";

  const contacts = useLoad(() => api.contacts(), []);
  const staff = useLoad(() => api.staff(), []);
  const accounts = useLoad(() => api.accounts(), []);
  const partners = useLoad(() => api.partners(), []);
  // Duzenlemede ortagin bu araca bagli katkisi nakitteki sermayesine eklenerek gosterilir (kayitta yeniden hesaplanir)
  const boundHere = Object.fromEntries((v?.status === "IN_STOCK" ? existing?.shares ?? [] : [])
    .filter((s) => s.contactId).map((s) => [s.contactId!, s.contributionCents]));
  const purchaseAccountId = purchaseAccount ?? accounts.data?.[0]?.id ?? "";
  const [extraContacts, setExtraContacts] = useState<ContactResponse[]>([]);
  const allContacts = [...extraContacts, ...(contacts.data ?? [])];

  const actualCents = tlToCents(actual);
  const price = actualCents != null && !Number.isNaN(actualCents) ? actualCents : 0;
  const planned = lockedCents + rows.reduce((t, r) => t + (r.amountCents > 0 ? r.amountCents : 0), 0);
  const remaining = price - planned;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const amounts = [tlToCents(tramer), tlToCents(actual), tlToCents(official)];
    if (amounts.some((a) => Number.isNaN(a))) return setError("Tutarları 1.250.000 gibi yaz.");
    const yearNumber = Number(year), mileageNumber = Number(mileage.replace(/\./g, ""));
    if (!Number.isInteger(yearNumber) || yearNumber < 1950 || yearNumber > 2100) return setError("Model yılını kontrol et.");
    if (!Number.isInteger(mileageNumber) || mileageNumber < 0) return setError("Kilometreyi kontrol et.");
    if (!makeModel?.ready) return setError("Marka ve modeli seç.");
    if (makeModel.needsCatalogConsent) return setError("Listede olmayan marka / model için kataloğa eklemeyi onayla.");
    if (consignment) {
      if (!consignor) return setError("Konsinye araçta aracın sahibini seç.");
      const rate = percentToBp(commissionRate), fixed = tlToCents(commissionFixed);
      if (commissionType === "RATE" && (rate == null || Number.isNaN(rate))) return setError("Komisyon oranını 3 ya da 2,5 gibi yaz.");
      if (commissionType === "FIXED" && (fixed == null || Number.isNaN(fixed))) return setError("Sabit komisyonu 25.000 gibi yaz.");
    }
    if (coOwned) {
      const problem = ownersProblem(owners, profitDiffers, price, shopCut);
      if (problem) return setError(problem);
    }
    if (!consignment && payMode === "PLAN") {
      if (!seller) return setError("Senetli alışta kimden alındığını seç.");
      if (price <= 0 || remaining !== 0) return setError("Ödeme planının toplamı gerçek alış bedeline eşit olmalı.");
      if (rows.some((r) => rowProblem(r) != null)) return setError("Plan satırlarını tamamla.");
    }

    const request: VehicleRequest = {
      plate, make: makeModel.make, model: makeModel.model, modelYear: yearNumber, fuel, transmission, mileageKm: mileageNumber,
      color: color.trim() || null, tramerCents: amounts[0], notes: notes.trim() || null,
      purchaseDate, sellerId: seller?.id ?? null, boughtById: boughtBy || null,
      actualPurchaseCents: amounts[1], officialPurchaseCents: amounts[2],
      purchaseAccountId: !consignment && payMode === "CASH" ? purchaseAccountId || null : null,
      purchasePlan: !consignment && payMode === "PLAN" ? rows.map(toRequest) : null,
      ownership,
      consignorId: consignment ? consignor?.id ?? null : null,
      commissionType: consignment ? commissionType : null,
      commissionRateBp: consignment && commissionType === "RATE" ? percentToBp(commissionRate) : null,
      commissionCents: consignment && commissionType === "FIXED" ? tlToCents(commissionFixed) : null,
      shares: coOwned ? toShareRequests(owners, profitDiffers) : null,
      addToCatalog: makeModel.addToCatalog,
      catalogModel: makeModel.catalogModel,
      shopCutType: coOwned && shopCut.enabled ? shopCut.type : null,
      shopCutRateBp: coOwned && shopCut.enabled && shopCut.type === "RATE" ? percentToBp(shopCut.rate) : null,
      shopCutCents: coOwned && shopCut.enabled && shopCut.type === "FIXED" ? tlToCents(shopCut.fixed) : null,
    };
    setSaving(true);
    setError(null);
    try {
      onSaved(v ? await api.updateVehicle(v.id, request) : await api.addVehicle(request));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={v ? "Aracı düzenle" : "Araç ekle"}
      open
      wide={!consignment && payMode === "PLAN"}
      onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="vehicle-form" disabled={saving}>{saving ? "Kaydediliyor…" : "Kaydet"}</Button>
      </>}
    >
      <form id="vehicle-form" onSubmit={submit} className="space-y-6">
        <ErrorBox message={error} />

        <section className="space-y-3">
          <h3 className="font-cond font-semibold text-[18px]">Araç</h3>
          <Field label="Plaka">
            <input className={`${inputClass} font-cond font-bold tracking-[0.06em] text-[18px]`} value={plate}
              onChange={(e) => setPlate(formatPlate(e.target.value))} required autoCapitalize="characters" placeholder="34 ABC 123" />
          </Field>
          <MakeModelPicker initial={initialMakeModel} onChange={setMakeModel} />
          <Field label="Model yılı">
            <input className={`${inputClass} max-w-40`} value={year} onChange={(e) => setYear(e.target.value.replace(/\D/g, "").slice(0, 4))} required inputMode="numeric" placeholder="2021" />
          </Field>
          <Field label="Yakıt">
            <Segment<Fuel> value={fuel} onChange={setFuel} options={(Object.keys(FUEL) as Fuel[]).map((f) => ({ value: f, label: FUEL[f] }))} />
          </Field>
          <Field label="Vites">
            <Segment<Transmission> value={transmission} onChange={setTransmission} options={(Object.keys(TRANSMISSION) as Transmission[]).map((t) => ({ value: t, label: TRANSMISSION[t] }))} />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Kilometre"><NumberInput kind="integer" value={mileage} onValueChange={setMileage} required placeholder="64.000" /></Field>
            <Field label="Renk"><input className={inputClass} value={color} onChange={(e) => setColor(e.target.value)} placeholder="Beyaz" /></Field>
          </div>
          <Field label="Tramer (hasar kaydı)" hint="TL, yoksa boş bırak">
            <NumberInput value={tramer} onValueChange={setTramer} placeholder="18.500" />
          </Field>
        </section>

        <section className="space-y-3">
          <h3 className="font-cond font-semibold text-[18px]">Sahiplik</h3>
          <Segment<Ownership> value={ownership} onChange={setOwnership}
            options={[{ value: "DEALER", label: "Galeri" }, { value: "CONSIGNMENT", label: "Konsinye" }, { value: "CO_OWNED", label: "Ortak" }]} />
          {consignment && (
            <>
              <p className="text-[13px] text-murekkep-2">
                Araç galerinin malı değil: alışı kasaya yazılmaz. Satışta satış bedeli eksi komisyon sahibinin carisine borç yazılır.
              </p>
              <Field label="Aracın sahibi">
                <ContactPicker contacts={allContacts} selected={consignor} onSelect={setConsignor}
                  onCreated={(c) => setExtraContacts((l) => [c, ...l])} placeholder="İsim yaz ya da seç" />
              </Field>
              <div className="grid grid-cols-[auto_1fr] gap-3 items-start">
                <Field label="Komisyon">
                  <Segment<CommissionType> value={commissionType} onChange={setCommissionType}
                    options={[{ value: "RATE", label: "Oran %" }, { value: "FIXED", label: "Sabit TL" }]} />
                </Field>
                {commissionType === "RATE"
                  ? <Field label="Oran" hint="Satış bedelinin yüzdesi">
                      <NumberInput value={commissionRate} onValueChange={setCommissionRate} placeholder="3" preview={false} />
                    </Field>
                  : <Field label="Tutar" hint="TL">
                      <NumberInput value={commissionFixed} onValueChange={setCommissionFixed} placeholder="25.000" />
                    </Field>}
              </div>
            </>
          )}
          {coOwned && (
            <CoOwnersEditor rows={owners} onRows={setOwners} profitDiffers={profitDiffers} onProfitDiffers={setProfitDiffers}
              shopCut={shopCut} onShopCut={setShopCut} priceCents={price} contacts={allContacts}
              onContactCreated={(c) => setExtraContacts((l) => [c, ...l])} partners={partners.data ?? []} boundHere={boundHere} />
          )}
        </section>

        <section className="space-y-3">
          <h3 className="font-cond font-semibold text-[18px]">Alış</h3>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Alış tarihi"><input type="date" className={inputClass} value={purchaseDate} onChange={(e) => setPurchaseDate(e.target.value)} required /></Field>
            <Field label="Alan çalışan">
              <select className={inputClass} value={boughtBy} onChange={(e) => setBoughtBy(e.target.value)}>
                <option value="">Seçilmedi</option>
                {(staff.data ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </Field>
          </div>
          {!consignment && <>
          <Field label="Kimden alındı">
            <ContactPicker contacts={allContacts} selected={seller} onSelect={setSeller}
              onCreated={(c) => setExtraContacts((l) => [c, ...l])} placeholder="İsim yaz ya da seç" />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Gerçek alış bedeli" hint="Kâr bununla hesaplanır">
              <NumberInput value={actual} onValueChange={setActual} placeholder="1.250.000" />
            </Field>
            <Field label="Resmi alış bedeli" hint="Belgedeki tutar">
              <NumberInput value={official} onValueChange={setOfficial} placeholder="980.000" />
            </Field>
          </div>
          </>}
        </section>

        {!consignment && <section className="space-y-3">
          <h3 className="font-cond font-semibold text-[18px]">Ödeme</h3>
          <Segment<"CASH" | "PLAN"> value={payMode} onChange={setPayMode}
            options={[{ value: "CASH", label: "Peşin" }, { value: "PLAN", label: "Senetli / taksitli" }]} />
          {payMode === "CASH" && (
            <Field label="Ödeme nereden" hint="Gerçek alış bedeli bu hesaptan düşülür. Değiştirirsen eski kayıt ters hareketle düzeltilir.">
              <select className={inputClass} value={purchaseAccountId} onChange={(e) => setPurchaseAccount(e.target.value)}>
                {(accounts.data ?? []).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
                <option value="">Kasaya işleme</option>
              </select>
            </Field>
          )}
          {payMode === "PLAN" && (
            <>
              <p className="text-[13px] text-murekkep-2">
                Bedel satıcının carisine borç yazılır; her taksit ödendiğinde kasadan ya da bankadan düşülür.
                {!seller && <b className="text-uyari"> Kimden alındığını seç.</b>}
              </p>
              {locked.length > 0 && (
                <div className="rounded-[12px] border border-basari/30 bg-basari-zemin/50 px-3 py-2">
                  <p className="text-[13px] font-semibold text-basari">Ödenmiş taksitler (değiştirilemez): {tl(lockedCents)}</p>
                  <ul className="text-[13px] text-murekkep-2 mt-1 space-y-0.5">
                    {locked.map((i) => <li key={i.id}>{formatDate(i.dueDate)} · {METHOD[i.method]} · <span className="sayi text-[14px]">{tl(i.amountCents)}</span></li>)}
                  </ul>
                </div>
              )}
              <PlanBuilder baseCents={Math.max(0, price - lockedCents)} date={purchaseDate} mode={planMode} onMode={setPlanMode}
                rows={rows} onRows={setRows} remaining={remaining} accounts={accounts.data ?? []} organizations={[]}
                modes={["INSTALLMENTS", "MIXED"]} methods={["CASH", "TRANSFER", "PROMISSORY_NOTE"]}
                paidNowLabel="Şimdi ödendi" downHint="Şimdi ödenir" />
              <PlanSummary planned={planned} remaining={remaining} ready={price > 0} />
            </>
          )}
        </section>}

        <Field label="Notlar">
          <textarea className={`${inputClass} h-24 py-2`} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
      </form>
    </Modal>
  );
}
