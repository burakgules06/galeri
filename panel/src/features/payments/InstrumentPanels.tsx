// Alinmis cek / senet islemleri. Ciro: kime verildigi ve (varsa) o kisiye olan hangi acik odemeyi kapattigi; acik odeme
// secilmezse kisinin carisine odeme yazilir. Karsiliksiz: tahsilat ya da ciro geri alinir, borc yeniden acilir.
import { useState } from "react";
import { api } from "../../api/galeri";
import { formatDate, METHOD, tl, todayIso } from "../../shared/format";
import type { ContactResponse, ContactSummary, InstallmentResponse } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass } from "../../shared/ui/Common";
import { ContactPicker } from "../contacts/ContactPicker";

interface PanelProps { i: InstallmentResponse; onClose: () => void; onDone: () => void }

export function EndorsePanel({ i, onClose, onDone }: PanelProps) {
  const contacts = useLoad(() => api.contacts(), []);
  const payables = useLoad(() => api.installments({ direction: "PURCHASE" }), []);
  const [extra, setExtra] = useState<ContactResponse[]>([]);
  const [to, setTo] = useState<ContactSummary | null>(null);
  const [target, setTarget] = useState<string>("");
  const [date, setDate] = useState(todayIso());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const people = [...extra, ...(contacts.data ?? [])].filter((c) => c.id !== i.payer.id);
  const open = (payables.data ?? []).filter((p) => to && p.payer.id === to.id && p.status !== "PAID" && p.amountCents >= i.amountCents);
  const what = i.method === "CHEQUE" ? "Çek" : "Senet";

  const choose = (c: ContactSummary | null) => {
    setTo(c);
    // Bu kisiye olan ilk uygun acik odeme varsayilan secili gelir
    const first = (payables.data ?? []).find((p) => c && p.payer.id === c.id && p.status !== "PAID" && p.amountCents >= i.amountCents);
    setTarget(first?.id ?? "");
  };

  const submit = async () => {
    if (!to) return setError("Kime verildiğini seç.");
    setBusy(true);
    setError(null);
    try {
      await api.endorse(i.id, to.id, target || null, date);
      onDone();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-1 mb-3 rounded-[12px] bg-yuzey-2 border border-cizgi p-3 space-y-3">
      <ErrorBox message={error} />
      <div className="grid gap-3 sm:grid-cols-[1fr_170px]">
        <Field label={`${what} kime verildi`}>
          <ContactPicker contacts={people} selected={to} onSelect={choose}
            onCreated={(c) => setExtra((l) => [c, ...l])} placeholder="İsim yaz ya da seç" />
        </Field>
        <Field label="Tarih"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} /></Field>
      </div>
      {to && (
        <Field label="Hangi borca sayılsın">
          <div className="space-y-1.5">
            {open.map((p) => (
              <label key={p.id} className="flex items-center gap-2 text-[14px] cursor-pointer">
                <input type="radio" name={`target-${i.id}`} className="accent-plaka" checked={target === p.id} onChange={() => setTarget(p.id)} />
                <span>{p.vehicle?.plate ?? ""} · {METHOD[p.method]} · {formatDate(p.dueDate)} · <b className="sayi">{tl(p.amountCents)}</b></span>
                {p.amountCents > i.amountCents && <span className="text-murekkep-3">kalan {tl(p.amountCents - i.amountCents)} bekler</span>}
              </label>
            ))}
            <label className="flex items-center gap-2 text-[14px] cursor-pointer">
              <input type="radio" name={`target-${i.id}`} className="accent-plaka" checked={target === ""} onChange={() => setTarget("")} />
              <span>Carisine yaz</span>
            </label>
          </div>
        </Field>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="quiet" onClick={onClose}>Vazgeç</Button>
        <Button onClick={submit} disabled={busy || !to}>{busy ? "Kaydediliyor…" : `${tl(i.amountCents)} ciro et`}</Button>
      </div>
    </div>
  );
}

export function BouncePanel({ i, onClose, onDone }: PanelProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.bounce(i.id);
      onDone();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="mx-1 mb-3 rounded-[12px] bg-tehlike-zemin border border-tehlike/30 p-3 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
      <span className="text-[14px]">
        {i.endorsedTo ? `Ciro geri alınır; ${i.endorsedTo.name} ile olan borç yeniden açılır. ` : "Tahsilat geri alınır. "}
        {i.payer.name} yeniden borçlu olur.
      </span>
      {error && <ErrorBox message={error} />}
      <div className="flex gap-2 shrink-0">
        <Button variant="quiet" onClick={onClose}>Vazgeç</Button>
        <Button variant="danger" onClick={submit} disabled={busy}>Karşılıksız</Button>
      </div>
    </div>
  );
}
