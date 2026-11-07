import { useState } from "react";
import { api } from "../../api/galeri";
import { tl, tlToCents, todayIso } from "../../shared/format";
import { NumberInput } from "../../shared/ui/NumberInput";
import { Button, ErrorBox, Field, inputClass, Modal } from "../../shared/ui/Common";
import { errorMessage, useLoad } from "../../shared/useLoad";
import type { ContactResponse, ContactSummary, InstallmentRequest, VehicleDetail, VehicleDetailAdmin } from "../../shared/types";
import { ContactPicker } from "../contacts/ContactPicker";

interface Row { accountId: string; amount: string }

/**
 * Yetkili calisanin pesin satisi: alici, bedel ve paranin girdigi hesap(lar). Tamami simdi tahsil edilmis sayilir;
 * vadeli satisi yonetici girer. Satan calisan backend'de kendisi yazilir; tutar sonra calisana gosterilmez.
 */
export function StaffSaleForm({ open, onClose, vehicle, onSaved }: {
  open: boolean; onClose: () => void; vehicle: VehicleDetail; onSaved: (d: VehicleDetailAdmin | VehicleDetail) => void;
}) {
  return (
    <Modal title={`${vehicle.plate} · peşin satış`} open={open} onClose={onClose}>
      {open && <Content vehicle={vehicle} onClose={onClose} onSaved={onSaved} />}
    </Modal>
  );
}

function Content({ vehicle, onClose, onSaved }: { vehicle: VehicleDetail; onClose: () => void; onSaved: (d: VehicleDetailAdmin | VehicleDetail) => void }) {
  const contacts = useLoad(() => api.contacts(), []);
  const accounts = useLoad(() => api.accounts(), []);
  const [extra, setExtra] = useState<ContactResponse[]>([]);
  const [buyer, setBuyer] = useState<ContactSummary | null>(null);
  const [date, setDate] = useState(todayIso());
  const [price, setPrice] = useState("");
  const [rows, setRows] = useState<Row[]>([{ accountId: "", amount: "" }]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const list = accounts.data ?? [];
  const defaultAccount = (list.find((a) => a.type === "CASH") ?? list[0])?.id ?? "";
  const priceCents = tlToCents(price) ?? 0;
  const split = rows.length > 1;
  // Tek hesapsa tamami oraya; bolunduyse son satir kalani alir
  const amounts = rows.map((r, k) => !split ? priceCents
    : k === rows.length - 1 ? priceCents - rows.slice(0, -1).reduce((t, x) => t + (tlToCents(x.amount) ?? 0), 0)
    : tlToCents(r.amount) ?? 0);

  const submit = async () => {
    setError(null);
    if (!buyer) return setError("Alıcıyı seç.");
    if (priceCents <= 0) return setError("Satış bedelini yaz.");
    if (amounts.some((a) => a <= 0)) return setError("Hesaplara bölünen tutarlar satış bedelini aşmamalı.");
    const installments: InstallmentRequest[] = rows.map((r, k) => {
      const accountId = r.accountId || defaultAccount;
      const bank = list.find((a) => a.id === accountId)?.type === "BANK";
      return { dueDate: date, amountCents: amounts[k], method: bank ? "TRANSFER" : "CASH", noteNo: null, payerId: null, paidNow: true, accountId };
    });
    setSaving(true);
    try {
      onSaved(await api.sell(vehicle.id, { saleDate: date, buyerId: buyer.id, soldById: null, salePriceCents: priceCents, installments, ownerReturns: null }));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <ErrorBox message={error} />
      <p className="m-0 text-[13.5px] text-murekkep-2">Peşin satış: bedelin tamamı şimdi alınır. Vadeli satışı yönetici girer.</p>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Satış tarihi"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} /></Field>
        <Field label="Satış bedeli"><NumberInput value={price} onValueChange={setPrice} placeholder="1.250.000" /></Field>
      </div>
      <Field label="Alıcı">
        <ContactPicker contacts={[...extra, ...(contacts.data ?? [])]} selected={buyer} onSelect={setBuyer} role="CUSTOMER"
          onCreated={(c) => setExtra((l) => [c, ...l])} placeholder="İsim yaz ya da seç" />
      </Field>
      <div className="space-y-2">
        <p className="m-0 text-[14px] font-semibold">Para nereye girdi</p>
        {rows.map((r, k) => (
          <div key={k} className="grid grid-cols-[1fr_auto] gap-2 sm:grid-cols-[1fr_180px_auto] sm:items-center">
            <select className={inputClass} aria-label="Hesap" value={r.accountId || defaultAccount}
              onChange={(e) => setRows((l) => l.map((x, j) => (j === k ? { ...x, accountId: e.target.value } : x)))}>
              {list.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
            {split && k < rows.length - 1
              ? <div className="col-span-2 sm:col-span-1 order-last sm:order-none"><NumberInput value={r.amount} placeholder="Tutar"
                  onValueChange={(v) => setRows((l) => l.map((x, j) => (j === k ? { ...x, amount: v } : x)))} /></div>
              : <span className="col-span-2 sm:col-span-1 order-last sm:order-none sayi text-[15px] text-murekkep-2">{split ? `kalan ${tl(Math.max(0, amounts[k]))}` : priceCents > 0 ? tl(priceCents) : ""}</span>}
            {split
              ? <button type="button" aria-label="Çıkar" onClick={() => setRows((l) => l.filter((_, j) => j !== k))}
                  className="h-9 w-9 rounded-full border border-cizgi bg-yuzey text-murekkep-2 cursor-pointer">×</button>
              : <span />}
          </div>
        ))}
        {rows.length < 3 && (
          <button type="button" onClick={() => setRows((l) => [...l, { accountId: "", amount: "" }])}
            className="cursor-pointer border-none bg-transparent p-0 text-[14px] font-semibold text-plaka">+ Başka hesaba böl</button>
        )}
      </div>
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="quiet" onClick={onClose}>Vazgeç</Button>
        <Button onClick={submit} disabled={saving}>{saving ? "Kaydediliyor…" : "Satışı kaydet"}</Button>
      </div>
    </div>
  );
}
