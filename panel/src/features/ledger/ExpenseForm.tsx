// Masraf girisi: araca bagliysa aracin maliyetine eklenir, degilse genel gider. Odeme kasadan ya da bankadan.
// Konsinye aracta masraf secilen kasa / bankadan sahibinin sermayesinden duser (nakdi yetmezse onay sorulur, eksiye dusebilir);
// "Cepten" secilirse para hareketi olmaz, masraf sadece aracta gorunur.
// Yetkili calisan (canAddExpense): sadece arac masrafi, kasa / banka secer (bakiyesiz); cepten ve duzenleme yok.
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
import { CONSIGNOR_CASH_NEGATIVE, withNegativeConfirm } from "../../shared/consignorCash";
import { ApiError } from "../../shared/errors/ApiError";
import { useAuth } from "../../auth/useAuth";

interface VehicleChoice { id: string; plate: string; make: string; model: string; ownership?: Ownership }

interface Props {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
  /** Arac detayindan acilinca arac sabittir. */
  vehicle?: VehicleChoice;
  /** Duzenlenecek masraf (arac ve masraf turu sabit kalir). */
  existing?: LedgerEntryResponse;
  /** Dukkan sayfasindan: sadece genel gider (dukkan gideri). */
  shop?: boolean;
}

const POCKET = "POCKET";   // "Cepten (elden ödendi)": kasa / banka degismez
const HOUSE_CASH = "CASH";   // sermayedar: para galerinin kasasindan (hesap secmez)

export function ExpenseForm(props: Props) {
  return props.open ? <ExpenseFormContent {...props} /> : null;
}

function ExpenseFormContent({ onClose, onSaved, vehicle: fixedVehicle, existing, shop = false }: Props) {
  const { isInvestor, canAddExpense: staff } = useAuth();   // sermayedar: kendi aracina masraf; para galerinin kasasindan (hesap secmez)
  // Duzenlemede arac kaydin kendisinden; aracin maliyet hesabina gitmeyen arac masrafi konsinye aractadir
  const vehicle: VehicleChoice | undefined = existing?.vehicle
    ? { ...existing.vehicle, ownership: fixedVehicle?.ownership ?? (existing.target.type !== "VEHICLE" ? "CONSIGNMENT" : undefined) }
    : fixedVehicle;
  const locked = !!existing;
  const [root, setRoot] = useState<ExpenseRoot>(vehicle ? "VEHICLE" : "GENERAL");
  const [vehicleId, setVehicleId] = useState(vehicle?.id ?? "");
  const [amount, setAmount] = useState(existing ? centsToInput(existing.amountCents) : "");
  const [category, setCategory] = useState(existing?.category ?? "");
  const [accountId, setAccountId] = useState(existing ? (existing.source.type === "CAPITAL" ? POCKET : existing.source.id) : "");
  const [date, setDate] = useState(existing?.date ?? todayIso());
  const [description, setDescription] = useState(existing?.description ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const accounts = useLoad(() => api.accounts(), []);
  const stock = useLoad(() => (vehicle ? Promise.resolve([]) : api.vehicles("", "IN_STOCK")), [vehicle?.id]);
  const ownership = vehicle?.ownership ?? stock.data?.find((v) => v.id === vehicleId)?.ownership;
  const consignment = root === "VEHICLE" && ownership === "CONSIGNMENT";
  const selectedAccount = accountId || (isInvestor ? HOUSE_CASH : (accounts.data?.find((a) => a.type === "CASH") ?? accounts.data?.[0])?.id || "");
  const pocket = !staff && (root === "GENERAL" || (consignment && !locked)) && selectedAccount === POCKET;
  const pocketVehicle = pocket && consignment;   // konsinye araca cepten: sadece takip, para hareketi yok

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const cents = tlToCents(amount);
    if (cents == null || Number.isNaN(cents) || cents <= 0) return setError("Tutarı 5.000 gibi yaz.");
    if (!category.trim()) return setError("Kategori yaz ya da seç.");
    if (root === "VEHICLE" && !vehicleId) return setError("Aracı seç.");
    if (!selectedAccount && !isInvestor) return setError("Ödemenin çıktığı hesabı seç.");
    // Konsinye masraf sahibinin parasindan duser: dukkan sahibinin kasa payi sorulmaz
    if (!existing && !pocket && !consignment && !isInvestor && !staff && !(await confirmOwnerCash(cents))) return;
    setSaving(true);
    setError(null);
    try {
      const request = {
        date, sourceAccountId: pocket || isInvestor ? null : selectedAccount, paidByOwner: pocket && !pocketVehicle, amountCents: cents,
        vehicleId: root === "VEHICLE" ? vehicleId : null, categoryId: null, categoryName: category.trim(),
        description: description.trim() || null,
      };
      if (staff) {
        await api.addExpense({ ...request, confirmNegative: false });
      } else if (pocketVehicle) {
        await api.addPocketExpense(request);
      } else {
        const saved = await withNegativeConfirm((confirmNegative) => existing
          ? api.updateExpense(existing.id, { ...request, confirmNegative })
          : api.addExpense({ ...request, confirmNegative }));
        if (saved == null) return;
      }
      onSaved();
    } catch (err) {
      // Calisan: konsinye sahibinin parasi yetmiyor; eksiye dusurme karari yoneticinin
      setError(staff && err instanceof ApiError && err.code === CONSIGNOR_CASH_NEGATIVE
        ? "Bu masraf araç sahibinin parasını aşıyor. Yöneticiye girdirin." : errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={`${locked ? "Masrafı düzenle" : shop ? "Dükkân gideri ekle" : "Masraf ekle"}${vehicle ? ` · ${vehicle.plate}` : ""}`} open onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="expense-form" disabled={saving}>{saving ? "Kaydediliyor…" : locked ? "Değişikliği kaydet" : "Masrafı kaydet"}</Button>
      </>}>
      <form id="expense-form" onSubmit={submit} className="space-y-4">
        <ErrorBox message={error} />

        {!vehicle && !locked && !shop && (
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

        <Field label="Tutar">
          <NumberInput className={`${inputClass} font-cond text-[18px]`} value={amount} onValueChange={setAmount}
            placeholder="5.000" autoFocus />
        </Field>

        <Field label="Kategori">
          <CategoryPicker key={root} root={root} name={category} onChange={setCategory} />
        </Field>

        {isInvestor && !(consignment && !locked) ? <p className="text-[13.5px] text-murekkep-2">Para galerinin kasasından çıkar; aracın maliyetine eklenir.</p> : <Field label="Nereden ödendi">
          {isInvestor
            ? <Segment<string> value={selectedAccount} onChange={setAccountId}
                options={[{ value: HOUSE_CASH, label: "Kasadan" }, { value: POCKET, label: "Cepten" }]} />
            : accounts.data && (
              <Segment<string> value={selectedAccount} onChange={setAccountId}
                options={[...accounts.data.map((a) => ({ value: a.id, label: staff ? a.name : `${a.name} · ${tl(a.balanceCents)}` })),
                  ...(!staff && (root === "GENERAL" || (consignment && !locked)) ? [{ value: POCKET, label: root === "GENERAL" ? "Cepten (elden ödendi)" : "Cepten" }] : [])]} />
            )}
          {pocket && !pocketVehicle && <p className="text-[13px] text-murekkep-2 mt-1.5">Kasa ve banka değişmez, kimsenin sermayesinden düşmez; gider olarak kaydedilir.</p>}
        </Field>}

        <div className="grid grid-cols-2 gap-3">
          <Field label="Tarih"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} required /></Field>
          <Field label="Açıklama"><input className={inputClass} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="İsteğe bağlı" /></Field>
        </div>
      </form>
    </Modal>
  );
}
