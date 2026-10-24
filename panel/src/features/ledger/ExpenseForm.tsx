// Masraf girisi: araca bagliysa aracin maliyetine eklenir, degilse genel gider. Odeme kasadan ya da bankadan.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { tl, tlToCents, todayIso } from "../../shared/format";
import type { ExpenseRoot, LedgerEntryResponse } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { CategoryPicker } from "./CategoryPicker";

interface VehicleChoice { id: string; plate: string; make: string; model: string }

interface Props {
  open: boolean;
  onClose: () => void;
  onSaved: (e: LedgerEntryResponse) => void;
  /** Arac detayindan acilinca arac sabittir. */
  vehicle?: VehicleChoice;
}

export function ExpenseForm(props: Props) {
  return props.open ? <ExpenseFormContent {...props} /> : null;
}

function ExpenseFormContent({ onClose, onSaved, vehicle }: Props) {
  const [root, setRoot] = useState<ExpenseRoot>(vehicle ? "VEHICLE" : "GENERAL");
  const [vehicleId, setVehicleId] = useState(vehicle?.id ?? "");
  const [amount, setAmount] = useState("");
  const [category, setCategory] = useState("");
  const [accountId, setAccountId] = useState("");
  const [date, setDate] = useState(todayIso());
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const accounts = useLoad(() => api.accounts(), []);
  const stock = useLoad(() => (vehicle ? Promise.resolve([]) : api.vehicles("", "IN_STOCK")), [vehicle?.id]);
  const selectedAccount = accountId || accounts.data?.[0]?.id || "";

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const cents = tlToCents(amount);
    if (cents == null || Number.isNaN(cents) || cents <= 0) return setError("Tutarı 5.000 gibi yaz.");
    if (!category.trim()) return setError("Kategori yaz ya da seç.");
    if (root === "VEHICLE" && !vehicleId) return setError("Aracı seç.");
    if (!selectedAccount) return setError("Ödemenin çıktığı hesabı seç.");
    setSaving(true);
    setError(null);
    try {
      onSaved(await api.addExpense({
        date, sourceAccountId: selectedAccount, amountCents: cents,
        vehicleId: root === "VEHICLE" ? vehicleId : null, categoryId: null, categoryName: category.trim(),
        description: description.trim() || null,
      }));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={vehicle ? `Masraf ekle · ${vehicle.plate}` : "Masraf ekle"} open onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="expense-form" disabled={saving}>{saving ? "Kaydediliyor…" : "Masrafı kaydet"}</Button>
      </>}>
      <form id="expense-form" onSubmit={submit} className="space-y-4">
        <ErrorBox message={error} />

        {!vehicle && (
          <Field label="Masraf türü">
            <Segment<ExpenseRoot> value={root} onChange={(r) => { setRoot(r); setCategory(""); }}
              options={[{ value: "GENERAL", label: "Genel gider" }, { value: "VEHICLE", label: "Araca bağlı" }]} />
          </Field>
        )}

        {!vehicle && root === "VEHICLE" && (
          <Field label="Araç" hint="Masraf aracın maliyetine eklenir">
            <select className={inputClass} value={vehicleId} onChange={(e) => setVehicleId(e.target.value)}>
              <option value="">Seç</option>
              {(stock.data ?? []).map((v) => <option key={v.id} value={v.id}>{v.plate} · {v.make} {v.model}</option>)}
            </select>
          </Field>
        )}

        <Field label="Tutar" hint="TL">
          <input className={`${inputClass} font-cond text-[18px]`} value={amount} onChange={(e) => setAmount(e.target.value)}
            inputMode="decimal" placeholder="5.000" autoFocus />
        </Field>

        <Field label="Kategori">
          <CategoryPicker key={root} root={root} name={category} onChange={setCategory} />
        </Field>

        <Field label="Nereden ödendi">
          {accounts.data && (
            <Segment<string> value={selectedAccount} onChange={setAccountId}
              options={accounts.data.map((a) => ({ value: a.id, label: `${a.name} · ${tl(a.balanceCents)}` }))} />
          )}
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="Tarih"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} required /></Field>
          <Field label="Açıklama"><input className={inputClass} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="İsteğe bağlı" /></Field>
        </div>
      </form>
    </Modal>
  );
}
