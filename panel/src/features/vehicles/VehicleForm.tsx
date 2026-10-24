// Arac ekleme / duzenleme. Sadece ADMIN acar. Tutarlar TL girilir, kurus gonderilir.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { centsToInput, formatPlate, FUEL, tlToCents, todayIso, TRANSMISSION } from "../../shared/format";
import type { ContactResponse, ContactSummary, Fuel, Transmission, VehicleDetailAdmin, VehicleRequest } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { ContactPicker } from "../contacts/ContactPicker";

interface Props {
  open: boolean;
  onClose: () => void;
  onSaved: (d: VehicleDetailAdmin) => void;
  existing?: VehicleDetailAdmin;
}

export function VehicleForm(props: Props) {
  return props.open ? <VehicleFormContent {...props} /> : null;
}

function VehicleFormContent({ onClose, onSaved, existing }: Props) {
  const v = existing?.vehicle;
  const [plate, setPlate] = useState(v?.plate ?? "");
  const [make, setMake] = useState(v?.make ?? "");
  const [model, setModel] = useState(v?.model ?? "");
  const [year, setYear] = useState(v ? String(v.modelYear) : "");
  const [fuel, setFuel] = useState<Fuel>(v?.fuel ?? "GASOLINE");
  const [transmission, setTransmission] = useState<Transmission>(v?.transmission ?? "MANUAL");
  const [mileage, setMileage] = useState(v ? String(v.mileageKm) : "");
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
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const contacts = useLoad(() => api.contacts(), []);
  const staff = useLoad(() => api.staff(), []);
  const accounts = useLoad(() => api.accounts(), []);
  const purchaseAccountId = purchaseAccount ?? accounts.data?.[0]?.id ?? "";
  const [extraContacts, setExtraContacts] = useState<ContactResponse[]>([]);
  const allContacts = [...extraContacts, ...(contacts.data ?? [])];

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const amounts = [tlToCents(tramer), tlToCents(actual), tlToCents(official)];
    if (amounts.some((a) => Number.isNaN(a))) return setError("Tutarları 1.250.000 gibi yaz.");
    const yearNumber = Number(year), mileageNumber = Number(mileage.replace(/\./g, ""));
    if (!Number.isInteger(yearNumber) || yearNumber < 1950 || yearNumber > 2100) return setError("Model yılını kontrol et.");
    if (!Number.isInteger(mileageNumber) || mileageNumber < 0) return setError("Kilometreyi kontrol et.");

    const request: VehicleRequest = {
      plate, make, model, modelYear: yearNumber, fuel, transmission, mileageKm: mileageNumber,
      color: color.trim() || null, tramerCents: amounts[0], notes: notes.trim() || null,
      purchaseDate, sellerId: seller?.id ?? null, boughtById: boughtBy || null,
      actualPurchaseCents: amounts[1], officialPurchaseCents: amounts[2], purchaseAccountId: purchaseAccountId || null,
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
          <div className="grid grid-cols-2 gap-3">
            <Field label="Marka"><input className={inputClass} value={make} onChange={(e) => setMake(e.target.value)} required placeholder="Volkswagen" /></Field>
            <Field label="Model yılı"><input className={inputClass} value={year} onChange={(e) => setYear(e.target.value)} required inputMode="numeric" placeholder="2021" /></Field>
          </div>
          <Field label="Model"><input className={inputClass} value={model} onChange={(e) => setModel(e.target.value)} required placeholder="Passat 1.5 TSI Business" /></Field>
          <Field label="Yakıt">
            <Segment<Fuel> value={fuel} onChange={setFuel} options={(Object.keys(FUEL) as Fuel[]).map((f) => ({ value: f, label: FUEL[f] }))} />
          </Field>
          <Field label="Vites">
            <Segment<Transmission> value={transmission} onChange={setTransmission} options={(Object.keys(TRANSMISSION) as Transmission[]).map((t) => ({ value: t, label: TRANSMISSION[t] }))} />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Kilometre"><input className={inputClass} value={mileage} onChange={(e) => setMileage(e.target.value)} required inputMode="numeric" placeholder="64000" /></Field>
            <Field label="Renk"><input className={inputClass} value={color} onChange={(e) => setColor(e.target.value)} placeholder="Beyaz" /></Field>
          </div>
          <Field label="Tramer (hasar kaydı)" hint="TL, yoksa boş bırak">
            <input className={inputClass} value={tramer} onChange={(e) => setTramer(e.target.value)} inputMode="decimal" placeholder="18.500" />
          </Field>
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
          <Field label="Kimden alındı">
            <ContactPicker contacts={allContacts} selected={seller} onSelect={setSeller}
              onCreated={(c) => setExtraContacts((l) => [c, ...l])} placeholder="İsim yaz ya da seç" />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Gerçek alış bedeli" hint="Kâr bununla hesaplanır">
              <input className={inputClass} value={actual} onChange={(e) => setActual(e.target.value)} inputMode="decimal" placeholder="1.250.000" />
            </Field>
            <Field label="Resmi alış bedeli" hint="Belgedeki tutar">
              <input className={inputClass} value={official} onChange={(e) => setOfficial(e.target.value)} inputMode="decimal" placeholder="980.000" />
            </Field>
          </div>
          <Field label="Ödeme nereden" hint="Peşin alışta gerçek alış bedeli bu hesaptan düşülür. Değiştirirsen eski kayıt ters hareketle düzeltilir.">
            <select className={inputClass} value={purchaseAccountId} onChange={(e) => setPurchaseAccount(e.target.value)}>
              {(accounts.data ?? []).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
              <option value="">Kasaya işleme (senetli vb.)</option>
            </select>
          </Field>
        </section>

        <Field label="Notlar">
          <textarea className={`${inputClass} h-24 py-2`} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
      </form>
    </Modal>
  );
}
