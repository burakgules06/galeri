// Araci satildi olarak isaretler. Odeme plani ve tahsilat 3. adimda gelecek.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { tlToCents, todayIso } from "../../shared/format";
import type { ContactResponse, ContactSummary, VehicleDetailAdmin } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal } from "../../shared/ui/Common";
import { ContactPicker } from "../contacts/ContactPicker";

interface Props { open: boolean; onClose: () => void; vehicle: VehicleDetailAdmin; onSaved: (d: VehicleDetailAdmin) => void }

export function SaleForm(props: Props) {
  return props.open ? <SaleFormContent {...props} /> : null;
}

function SaleFormContent({ onClose, vehicle, onSaved }: Props) {
  const [date, setDate] = useState(todayIso());
  const [buyer, setBuyer] = useState<ContactSummary | null>(null);
  const [soldBy, setSoldBy] = useState("");
  const [price, setPrice] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const contacts = useLoad(() => api.contacts(), []);
  const staff = useLoad(() => api.staff(), []);
  const [extraContacts, setExtraContacts] = useState<ContactResponse[]>([]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const cents = tlToCents(price);
    if (Number.isNaN(cents)) return setError("Satış bedelini 1.400.000 gibi yaz.");
    setSaving(true);
    setError(null);
    try {
      onSaved(await api.sell(vehicle.vehicle.id, { saleDate: date, buyerId: buyer?.id ?? null, soldById: soldBy || null, salePriceCents: cents }));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="Satıldı olarak işaretle" open onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="sale-form" disabled={saving}>{saving ? "Kaydediliyor…" : "Satışı kaydet"}</Button>
      </>}>
      <form id="sale-form" onSubmit={submit} className="space-y-3">
        <ErrorBox message={error} />
        <div className="grid grid-cols-2 gap-3">
          <Field label="Satış tarihi"><input type="date" className={inputClass} value={date} min={vehicle.vehicle.purchaseDate} onChange={(e) => setDate(e.target.value)} required /></Field>
          <Field label="Satan çalışan">
            <select className={inputClass} value={soldBy} onChange={(e) => setSoldBy(e.target.value)}>
              <option value="">Seçilmedi</option>
              {(staff.data ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
        </div>
        <Field label="Alıcı">
          <ContactPicker contacts={[...extraContacts, ...(contacts.data ?? [])]} selected={buyer} onSelect={setBuyer}
            onCreated={(c) => setExtraContacts((l) => [c, ...l])} placeholder="İsim yaz ya da seç" />
        </Field>
        <Field label="Satış bedeli" hint="TL. Ödeme planı ve taksitler bir sonraki sürümde.">
          <input className={inputClass} value={price} onChange={(e) => setPrice(e.target.value)} inputMode="decimal" placeholder="1.400.000" />
        </Field>
      </form>
    </Modal>
  );
}
