// Kisiye odeme: kasadan ya da bankadan kisinin carisine (ornegin konsinye sahibine satis parasi). Kismi olabilir.
// Yanlis girilirse Kasa ekranindan "Duzelt" ile ters hareket atilir.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { centsToInput, tl, tlToCents, todayIso } from "../../shared/format";
import type { ContactResponse } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";

export function ContactPaymentForm({ contact, debtCents, onClose, onSaved }: {
  contact: ContactResponse; debtCents: number; onClose: () => void; onSaved: () => void;
}) {
  const [amount, setAmount] = useState(debtCents > 0 ? centsToInput(debtCents) : "");
  const [accountId, setAccountId] = useState("");
  const [date, setDate] = useState(todayIso());
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const accounts = useLoad(() => api.accounts(), []);
  const selected = accountId || accounts.data?.[0]?.id || "";
  const cents = tlToCents(amount);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (cents == null || Number.isNaN(cents) || cents <= 0) return setError("Tutarı 250.000 gibi yaz.");
    if (!selected) return setError("Ödemenin çıktığı hesabı seç.");
    setSaving(true);
    setError(null);
    try {
      await api.payContact(contact.id, { accountId: selected, date, amountCents: cents, description: description.trim() || null });
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const after = debtCents - (cents != null && !Number.isNaN(cents) ? cents : 0);

  return (
    <Modal title={`${contact.name} · ödeme`} open onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="contact-payment-form" disabled={saving}>{saving ? "Kaydediliyor…" : "Ödemeyi kaydet"}</Button>
      </>}>
      <form id="contact-payment-form" onSubmit={submit} className="space-y-4">
        <ErrorBox message={error} />
        {debtCents > 0 && (
          <p className="rounded-lg bg-tehlike-zemin px-3 py-2 text-[14px]">
            Bu kişiye borcumuz <b className="sayi text-[16px] text-tehlike">{tl(debtCents)}</b>. Kısmi ödeme yapabilirsiniz.
          </p>
        )}
        <Field label="Tutar" hint={debtCents > 0 && cents && !Number.isNaN(cents)
          ? after > 0 ? `Ödemeden sonra kalan borç: ${tl(after)}` : after === 0 ? "Borç kapanır." : `Fazla ödeme: ${tl(-after)} (kişi bize borçlanır)`
          : "TL"}>
          <NumberInput className={`${inputClass} font-cond text-[18px]`} value={amount} onValueChange={setAmount}
            placeholder="250.000" autoFocus />
        </Field>
        <Field label="Nereden ödendi">
          {accounts.data && (
            <Segment<string> value={selected} onChange={setAccountId}
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
