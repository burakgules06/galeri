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

/**
 * Borcu elimdeki cek / senetle ode: acik alis taksiti icin portfoydeki (alinmis, odenmemis) evraklardan biri secilir ve
 * bu borca ciro edilir. Evrakin sahibinin borcu kapanir, bu kisiye olan borc o kadar duser; kasa degismez.
 * Evrak borctan kucukse borc bolunur, kalani ayni vadeyle acik kalir (sonra baska evrak ya da nakitle odenir).
 */
export function PayWithInstrumentPanel({ i, onClose, onDone }: PanelProps) {
  const portfolio = useLoad(() => api.installments({ direction: "SALE" }), []);
  const [picked, setPicked] = useState("");
  const [date, setDate] = useState(todayIso());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Ayni tutardakiler once, sonra buyukten kucuge; borcu asan ve borcun sahibinden alinmis evrak listelenmez
  const options = (portfolio.data ?? [])
    .filter((p) => p.status !== "PAID" && (p.method === "CHEQUE" || p.method === "PROMISSORY_NOTE")
      && p.amountCents <= i.amountCents && p.payer.id !== i.payer.id)
    .sort((a, b) => Number(b.amountCents === i.amountCents) - Number(a.amountCents === i.amountCents) || b.amountCents - a.amountCents);
  const chosen = options.find((p) => p.id === picked);

  const submit = async () => {
    if (!chosen) return setError("Verilen çek ya da senedi seç.");
    setBusy(true);
    setError(null);
    try {
      await api.endorse(chosen.id, i.payer.id, i.id, date);
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
      <Field label={`${i.payer.name} adına ciro edilecek çek / senet`}>
        {portfolio.data && options.length === 0 ? (
          <p className="m-0 text-[14px] text-murekkep-3">Elde bu borca verilebilecek çek ya da senet yok (tutarı {tl(i.amountCents)} veya altında olmalı).</p>
        ) : (
          <div className="space-y-1.5 max-h-72 overflow-y-auto">
            {options.map((p) => (
              <label key={p.id} className={`flex items-start gap-2 rounded-[10px] border px-2.5 py-2 text-[14px] cursor-pointer ${picked === p.id ? "border-plaka bg-yuzey" : "border-cizgi"}`}>
                <input type="radio" name={`instrument-${i.id}`} className="accent-plaka mt-1" checked={picked === p.id} onChange={() => setPicked(p.id)} />
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-baseline justify-between gap-x-3">
                    <span className="font-semibold">{METHOD[p.method]}{p.noteNo ? ` · ${p.noteNo}` : ""}</span>
                    <b className="sayi">{tl(p.amountCents)}</b>
                  </span>
                  <span className="block text-[13px] text-murekkep-3">
                    {p.payer.name} · vade {formatDate(p.dueDate)}{p.bankName ? ` · ${p.bankName}` : ""}{p.vehicle?.plate ? ` · ${p.vehicle.plate}` : ""}
                  </span>
                  {p.amountCents < i.amountCents && <span className="block text-[13px] text-murekkep-3">kalan {tl(i.amountCents - p.amountCents)} açık kalır</span>}
                </span>
              </label>
            ))}
          </div>
        )}
      </Field>
      <div className="grid gap-3 sm:grid-cols-[170px_1fr] sm:items-end">
        <Field label="Tarih"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} /></Field>
        <div className="flex justify-end gap-2">
          <Button variant="quiet" onClick={onClose}>Vazgeç</Button>
          <Button onClick={submit} disabled={busy || !chosen}>{busy ? "Kaydediliyor…" : chosen ? `${tl(chosen.amountCents)} ile öde` : "Öde"}</Button>
        </div>
      </div>
    </div>
  );
}
