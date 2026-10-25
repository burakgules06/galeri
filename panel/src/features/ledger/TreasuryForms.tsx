// Kasa islemleri: virman (kasa <-> banka) ve acilis bakiyesi (sisteme geciste, hesap basina bir kez).
// Yanlis girilen hareket Kasa listesinden "Duzelt" ile terslenir.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { tl, tlToCents, todayIso } from "../../shared/format";
import type { ContactResponse, ContactSummary, OpeningSide } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { ContactPicker } from "../contacts/ContactPicker";
import { NumberInput } from "../../shared/ui/NumberInput";

export function TransferForm({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const accounts = useLoad(() => api.accounts(), []);
  const list = accounts.data ?? [];
  const [fromId, setFromId] = useState("");
  const [toId, setToId] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(todayIso());
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const from = fromId || list[0]?.id || "";
  const to = toId || list.find((a) => a.id !== from)?.id || "";

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const cents = tlToCents(amount);
    if (cents == null || Number.isNaN(cents) || cents <= 0) return setError("Tutarı 50.000 gibi yaz.");
    if (!from || !to || from === to) return setError("İki farklı hesap seç.");
    setSaving(true);
    setError(null);
    try {
      await api.transfer({ fromAccountId: from, toAccountId: to, date, amountCents: cents, description: description.trim() || null });
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const options = list.map((a) => ({ value: a.id, label: `${a.name} · ${tl(a.balanceCents)}` }));
  return (
    <Modal title="Virman" open onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="transfer-form" disabled={saving || list.length < 2}>{saving ? "Kaydediliyor…" : "Aktar"}</Button>
      </>}>
      <form id="transfer-form" onSubmit={submit} className="space-y-4">
        <ErrorBox message={error} />
        {accounts.data && list.length < 2 && <p className="text-[14px] text-uyari">Virman için en az iki hesap gerekir; önce banka hesabı ekleyin.</p>}
        <Field label="Nereden"><Segment<string> value={from} onChange={setFromId} options={options} /></Field>
        <Field label="Nereye"><Segment<string> value={to} onChange={setToId} options={options.filter((o) => o.value !== from)} /></Field>
        <Field label="Tutar" hint="Toplam nakit değişmez; para bir hesaptan diğerine geçer.">
          <NumberInput className={`${inputClass} font-cond text-[18px]`} value={amount} onValueChange={setAmount}
            placeholder="50.000" autoFocus />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Tarih"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} required /></Field>
          <Field label="Açıklama"><input className={inputClass} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="İsteğe bağlı" /></Field>
        </div>
      </form>
    </Modal>
  );
}

export function OpeningBalanceForm({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const accounts = useLoad(() => api.accounts(), []);
  const contacts = useLoad(() => api.contacts(), []);
  const [extra, setExtra] = useState<ContactResponse[]>([]);
  const [kind, setKind] = useState<"money" | "contact">("money");
  const [accountId, setAccountId] = useState("");
  const [contact, setContact] = useState<ContactSummary | null>(null);
  const [side, setSide] = useState<OpeningSide>("RECEIVABLE");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(todayIso());
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const account = accountId || accounts.data?.[0]?.id || "";

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const cents = tlToCents(amount);
    if (cents == null || Number.isNaN(cents) || cents <= 0) return setError("Tutarı 120.000 gibi yaz.");
    if (kind === "contact" && !contact) return setError("Kişiyi seç.");
    setSaving(true);
    setError(null);
    try {
      await api.openingBalance(kind === "money"
        ? { accountId: account, contactId: null, side: null, date, amountCents: cents }
        : { accountId: null, contactId: contact!.id, side, date, amountCents: cents });
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="Açılış bakiyesi" open onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="opening-form" disabled={saving}>{saving ? "Kaydediliyor…" : "Kaydet"}</Button>
      </>}>
      <form id="opening-form" onSubmit={submit} className="space-y-4">
        <ErrorBox message={error} />
        <p className="text-[14px] text-murekkep-2">
          Sisteme geçişte bir kez girilir: kasadaki, bankadaki para ve kişilerle önceden kalan alacak / borç. Her hesap için bir kez.
        </p>
        <Field label="Ne için">
          <Segment<"money" | "contact"> value={kind} onChange={setKind}
            options={[{ value: "money", label: "Kasa / banka" }, { value: "contact", label: "Kişi carisi" }]} />
        </Field>
        {kind === "money" && accounts.data && (
          <Field label="Hesap">
            <Segment<string> value={account} onChange={setAccountId} options={accounts.data.map((a) => ({ value: a.id, label: a.name }))} />
          </Field>
        )}
        {kind === "contact" && (
          <>
            <Field label="Kişi">
              <ContactPicker contacts={[...extra, ...(contacts.data ?? [])]} selected={contact} onSelect={setContact}
                onCreated={(c) => setExtra((l) => [c, ...l])} placeholder="İsim yaz ya da seç" />
            </Field>
            <Field label="Yön">
              <Segment<OpeningSide> value={side} onChange={setSide}
                options={[{ value: "RECEIVABLE", label: "Bize borçlu (alacak)" }, { value: "PAYABLE", label: "Biz borçluyuz (verecek)" }]} />
            </Field>
          </>
        )}
        <div className="grid grid-cols-2 gap-3">
          <Field label="Tutar" hint="TL">
            <NumberInput className={`${inputClass} font-cond text-[18px]`} value={amount} onValueChange={setAmount} placeholder="120.000" />
          </Field>
          <Field label="Tarih" hint="Sisteme geçiş günü"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} required /></Field>
        </div>
      </form>
    </Modal>
  );
}
