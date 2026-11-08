// Kazanc ekle (dukkan sahibi): noter / kredi / sigorta komisyonu, kira geliri gibi satis disi para girisi.
// Secilen kasa / bankaya girer. Araca baglanirsa o aracin karina (satis donemine), baglanmazsa donem karina "Diger kazanc"
// olarak eklenir. Yanlis girilirse Kasa'da "Düzelt" ile ters kayit atilir.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { tl, tlToCents, todayIso } from "../../shared/format";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";
import { CategoryPicker } from "./CategoryPicker";

interface VehicleChoice { id: string; plate: string; make: string; model: string }

export function IncomeForm({ open, vehicle, onClose, onSaved }: {
  open: boolean; vehicle?: VehicleChoice; onClose: () => void; onSaved: () => void;
}) {
  if (!open) return null;
  return <IncomeFormBody vehicle={vehicle} onClose={onClose} onSaved={onSaved} />;
}

function IncomeFormBody({ vehicle, onClose, onSaved }: { vehicle?: VehicleChoice; onClose: () => void; onSaved: () => void }) {
  const accounts = useLoad(() => api.accounts(), []);
  const vehicles = useLoad(() => (vehicle ? Promise.resolve([]) : api.vehicles("", null)), [vehicle?.id]);
  const [linked, setLinked] = useState<"GENERAL" | "VEHICLE">(vehicle ? "VEHICLE" : "GENERAL");
  const [vehicleId, setVehicleId] = useState(vehicle?.id ?? "");
  const [amount, setAmount] = useState("");
  const [category, setCategory] = useState("");
  const [accountId, setAccountId] = useState("");
  const [date, setDate] = useState(todayIso());
  const [description, setDescription] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const selectedAccount = accountId || accounts.data?.[0]?.id || "";

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const cents = tlToCents(amount);
    if (!cents || cents <= 0) return setError("Tutarı yaz.");
    if (!category.trim()) return setError("Kategori seç ya da yaz (örn. Kredi komisyonu).");
    if (!selectedAccount) return setError("Paranın girdiği kasa ya da bankayı seç.");
    if (linked === "VEHICLE" && !vehicleId) return setError("Aracı seç.");
    setSaving(true);
    setError(null);
    try {
      await api.addIncome({
        date, targetAccountId: selectedAccount, amountCents: cents, vehicleId: linked === "VEHICLE" ? vehicleId : null,
        categoryId: null, categoryName: category.trim(), parentCategoryId: null, description: description.trim() || null,
      });
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={`Kazanç ekle${vehicle ? ` · ${vehicle.plate}` : ""}`} open onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="income-form" disabled={saving}>{saving ? "Kaydediliyor…" : "Kazancı kaydet"}</Button>
      </>}>
      <form id="income-form" onSubmit={submit} className="space-y-4">
        <ErrorBox message={error} />

        {!vehicle && (
          <Field label="Kazanç türü">
            <Segment<"GENERAL" | "VEHICLE"> value={linked} onChange={setLinked}
              options={[{ value: "GENERAL", label: "Genel kazanç" }, { value: "VEHICLE", label: "Araca bağlı" }]} />
          </Field>
        )}
        {!vehicle && linked === "VEHICLE" && (
          <Field label="Araç" hint="Kazanç bu aracın kârına eklenir (araç satıldığı dönemde).">
            <select className={inputClass} value={vehicleId} onChange={(e) => setVehicleId(e.target.value)}>
              <option value="">Seç</option>
              {(vehicles.data ?? []).map((v) => <option key={v.id} value={v.id}>{v.plate} · {v.make} {v.model}{v.status === "SOLD" ? " (satıldı)" : ""}</option>)}
            </select>
          </Field>
        )}

        <Field label="Tutar">
          <NumberInput className={`${inputClass} font-cond text-[18px]`} value={amount} onValueChange={setAmount} placeholder="5.000" autoFocus />
        </Field>

        <Field label="Kategori">
          <CategoryPicker root="INCOME" name={category} onChange={setCategory} />
        </Field>

        <Field label="Nereye girdi">
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
