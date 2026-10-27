// Masraf girisi: araca bagliysa aracin maliyetine eklenir, degilse genel gider. Odeme kasadan ya da bankadan.
// Konsinye aracta maliyet yok: masraf ya sahibine yansitilir (sahibi galeriye borclanir) ya da galerinin genel gideridir.
// existing verilirse duzenleme: form mevcut degerlerle acilir, kaydedince eski kayit terslenir, yenisi yazilir (PUT /api/expenses/{id}).
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { centsToInput, tl, tlToCents, todayIso } from "../../shared/format";
import type { ExpenseRoot, LedgerEntryResponse, Ownership } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { CategoryPicker } from "./CategoryPicker";
import { NumberInput } from "../../shared/ui/NumberInput";
import { confirmOwnerCash } from "../../shared/ownerCash";

interface VehicleChoice { id: string; plate: string; make: string; model: string; ownership?: Ownership }

interface Props {
  open: boolean;
  onClose: () => void;
  onSaved: (e: LedgerEntryResponse) => void;
  /** Arac detayindan acilinca arac sabittir. */
  vehicle?: VehicleChoice;
  /** Duzenlenecek masraf (arac ve masraf turu sabit kalir). */
  existing?: LedgerEntryResponse;
}

export function ExpenseForm(props: Props) {
  return props.open ? <ExpenseFormContent {...props} /> : null;
}

function ExpenseFormContent({ onClose, onSaved, vehicle: fixedVehicle, existing }: Props) {
  // Duzenlemede arac kaydin kendisinden; aracin maliyet hesabina gitmeyen arac masrafi konsinye aractadir
  const vehicle: VehicleChoice | undefined = existing?.vehicle
    ? { ...existing.vehicle, ownership: fixedVehicle?.ownership ?? (existing.target.type !== "VEHICLE" ? "CONSIGNMENT" : undefined) }
    : fixedVehicle;
  const locked = !!existing;
  const [root, setRoot] = useState<ExpenseRoot>(vehicle ? "VEHICLE" : "GENERAL");
  const [vehicleId, setVehicleId] = useState(vehicle?.id ?? "");
  const [amount, setAmount] = useState(existing ? centsToInput(existing.amountCents) : "");
  const [category, setCategory] = useState(existing?.category ?? "");
  const [accountId, setAccountId] = useState(existing?.source.id ?? "");
  const [date, setDate] = useState(existing?.date ?? todayIso());
  const [description, setDescription] = useState(existing?.description ?? "");
  const [chargeToOwner, setChargeToOwner] = useState(existing?.target.type === "BALANCE");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const accounts = useLoad(() => api.accounts(), []);
  const stock = useLoad(() => (vehicle ? Promise.resolve([]) : api.vehicles("", "IN_STOCK")), [vehicle?.id]);
  const selectedAccount = accountId || accounts.data?.[0]?.id || "";
  const ownership = vehicle?.ownership ?? stock.data?.find((v) => v.id === vehicleId)?.ownership;
  const consignment = root === "VEHICLE" && ownership === "CONSIGNMENT";

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const cents = tlToCents(amount);
    if (cents == null || Number.isNaN(cents) || cents <= 0) return setError("Tutarı 5.000 gibi yaz.");
    if (!category.trim()) return setError("Kategori yaz ya da seç.");
    if (root === "VEHICLE" && !vehicleId) return setError("Aracı seç.");
    if (!selectedAccount) return setError("Ödemenin çıktığı hesabı seç.");
    if (!existing && !(await confirmOwnerCash(cents))) return;
    setSaving(true);
    setError(null);
    try {
      const request = {
        date, sourceAccountId: selectedAccount, amountCents: cents,
        vehicleId: root === "VEHICLE" ? vehicleId : null, categoryId: null, categoryName: category.trim(),
        description: description.trim() || null,
        chargeToOwner: consignment ? chargeToOwner : null,
      };
      onSaved(await (existing ? api.updateExpense(existing.id, request) : api.addExpense(request)));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={`${locked ? "Masrafı düzenle" : "Masraf ekle"}${vehicle ? ` · ${vehicle.plate}` : ""}`} open onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="expense-form" disabled={saving}>{saving ? "Kaydediliyor…" : locked ? "Değişikliği kaydet" : "Masrafı kaydet"}</Button>
      </>}>
      <form id="expense-form" onSubmit={submit} className="space-y-4">
        <ErrorBox message={error} />

        {!vehicle && !locked && (
          <Field label="Masraf türü">
            <Segment<ExpenseRoot> value={root} onChange={(r) => { setRoot(r); setCategory(""); }}
              options={[{ value: "GENERAL", label: "Genel gider" }, { value: "VEHICLE", label: "Araca bağlı" }]} />
          </Field>
        )}

        {!vehicle && root === "VEHICLE" && (
          <Field label="Araç">
            <select className={inputClass} value={vehicleId} onChange={(e) => setVehicleId(e.target.value)}>
              <option value="">Seç</option>
              {(stock.data ?? []).map((v) => <option key={v.id} value={v.id}>{v.plate} · {v.make} {v.model}</option>)}
            </select>
          </Field>
        )}

        {consignment && (
          <Field label="Konsinye araç: masrafı kim üstleniyor?">
            <Segment<"owner" | "dealer"> value={chargeToOwner ? "owner" : "dealer"} onChange={(v) => setChargeToOwner(v === "owner")}
              options={[{ value: "dealer", label: "Galeri üstlenir" }, { value: "owner", label: "Sahibine yansıt" }]} />
          </Field>
        )}

        <Field label="Tutar">
          <NumberInput className={`${inputClass} font-cond text-[18px]`} value={amount} onValueChange={setAmount}
            placeholder="5.000" autoFocus />
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
