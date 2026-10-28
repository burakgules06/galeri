// Taksit satiri: odeyen, (istenirse) arac, vade + durum, tutar, yontem. Islemler satirin altinda acilir:
// tahsil et (hesap + tarih, senet / taksitte kismi tutar), geri al (onayla), vade / evrak no duzenle;
// alinmis cek / senette ciro et ve karsiliksiz. Durum renkleri: odendi yesil, bekliyor turuncu, gecikti kirmizi.
import { useState } from "react";
import { api } from "../../api/galeri";
import { centsToInput, dueLabel, formatDate, formatPhone, METHOD, tl, tlToCents, todayIso } from "../../shared/format";
import type { InstallmentResponse, InstallmentStatus } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Segment } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";
import { Plate } from "../../shared/ui/Plate";
import { BouncePanel, EndorsePanel } from "./InstrumentPanels";

const PILL: Record<InstallmentStatus, string> = {
  PAID: "bg-basari-zemin text-basari",
  UPCOMING: "bg-uyari-zemin text-uyari",
  OVERDUE: "bg-tehlike-zemin text-tehlike",
};

const STRIPE: Record<InstallmentStatus, string> = {
  PAID: "bg-basari-canli",
  UPCOMING: "bg-boyali",
  OVERDUE: "bg-tehlike-canli",
};

type Panel = "collect" | "undo" | "edit" | "endorse" | "bounce" | null;

export function InstallmentRow({ i, onChange, showVehicle = true, showPayer = true, allowDeleteOpenItem = false }: {
  i: InstallmentResponse;
  onChange: () => void;
  showVehicle?: boolean;
  showPayer?: boolean;
  allowDeleteOpenItem?: boolean;
}) {
  const [panel, setPanel] = useState<Panel>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const paid = i.status === "PAID";
  const actionLabel = i.direction === "SALE" ? "Tahsil et" : "Öde";
  // Alinmis cek / senet: ciro edilebilir, karsiliksiz cikabilir
  const instrument = i.direction === "SALE" && (i.method === "CHEQUE" || i.method === "PROMISSORY_NOTE");
  const canDeleteOpenItem = allowDeleteOpenItem && !i.vehicle && !paid
    && (i.method === "CHEQUE" || i.method === "PROMISSORY_NOTE" || i.method === "OPEN_ACCOUNT");
  const removeOpenItem = async () => {
    if (!window.confirm(`${METHOD[i.method]} · ${tl(i.amountCents)} kaydı silinsin mi? Cari açılış tutarı yeniden hesaplanır.`)) return;
    try {
      setDeleteError(null);
      await api.removeOpenItemInstallment(i.planId, i.id);
      onChange();
    } catch (e) {
      setDeleteError(errorMessage(e));
    }
  };

  return (
    <li className="relative">
      <span className={`absolute left-0 top-3 bottom-3 w-[3px] rounded-r ${STRIPE[i.status]}`} />
      <div className="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-4 pl-4 pr-1 py-3">
        <div className="min-w-0 grow">
          <div className="flex items-center gap-2 flex-wrap">
            {showPayer && <span className="font-semibold text-[15.5px] truncate">{i.payer.name}</span>}
            {!showPayer && <span className="font-semibold text-[15px]">{i.downPayment ? (i.count === 1 ? "Peşin" : "Peşinat") : `${i.number}. taksit`}</span>}
            <span className={`text-[12px] font-semibold rounded-full px-2 py-0.5 ${PILL[i.status]}`}>
              {i.endorsedTo ? `Ciro: ${i.endorsedTo.name}` : dueLabel(i)}
            </span>
            {i.bounced && <span className="text-[12px] font-semibold rounded-full px-2 py-0.5 bg-tehlike text-white">Karşılıksız</span>}
            {i.payer.id !== i.counterparty.id && showPayer && (
              <span className="text-[12px] text-murekkep-3">alıcı: {i.counterparty.name}</span>
            )}
          </div>
          <div className="mt-1 flex items-center gap-2 flex-wrap text-[13.5px] text-murekkep-2">
            {showVehicle && i.vehicle && (
              <a href={`#/vehicles/${i.vehicle.id}`} className="inline-flex items-center gap-1.5 hover:text-murekkep">
                <Plate plate={i.vehicle.plate} size="sm" />
                <span className="hidden md:inline truncate">{i.vehicle.make} {i.vehicle.model}</span>
              </a>
            )}
            <span>{formatDate(i.dueDate)}</span>
            <span className="text-murekkep-3">·</span>
            <span>{METHOD[i.method]}{i.noteNo ? ` no ${i.noteNo}` : ""}{i.bankName ? ` · ${i.bankName}` : ""}</span>
            {i.drawerName && <span className="text-murekkep-3">keşideci {i.drawerName}</span>}
            {i.description && <span className="max-w-full break-words text-murekkep-2">{i.description}</span>}
            {i.capitalCents > 0 && <span className="text-plaka-koyu font-semibold">Sermayeden {tl(i.capitalCents)}</span>}
            {i.holder && <span className="text-[12px] font-semibold text-plaka-koyu bg-plaka-acik rounded px-1.5 py-0.5">{i.holder.name}</span>}
            {showPayer && <><span className="text-murekkep-3">·</span><span>{i.downPayment ? "Peşinat" : `${i.number}/${i.installmentCount}`}</span></>}
            {showPayer && i.payer.phone && <span className="sayi text-[14px] text-murekkep-3">{formatPhone(i.payer.phone)}</span>}
          </div>
        </div>
        <div className="flex items-center justify-between sm:justify-end gap-3 shrink-0">
          <span className={`sayi font-bold text-[21px] whitespace-nowrap ${paid ? "text-basari" : i.status === "OVERDUE" ? "text-tehlike" : "text-murekkep"}`}>
            {tl(i.amountCents)}
          </span>
          {!paid && panel === null && (
            <div className="flex flex-wrap justify-end gap-1.5">
              {canDeleteOpenItem && <Button variant="quiet" className="h-9 px-2.5 text-tehlike" onClick={removeOpenItem}>Sil</Button>}
              <Button variant="quiet" className="h-9 px-2.5" onClick={() => setPanel("edit")} aria-label="Vadeyi düzenle">Düzenle</Button>
              {instrument && <Button variant="secondary" className="h-9" onClick={() => setPanel("endorse")}>Ciro et</Button>}
              <Button className="h-9" onClick={() => setPanel("collect")}>{actionLabel}</Button>
            </div>
          )}
          {paid && panel === null && (
            <div className="flex gap-1.5">
              {instrument && <Button variant="quiet" className="h-9 text-tehlike" onClick={() => setPanel("bounce")}>Karşılıksız</Button>}
              <Button variant="quiet" className="h-9" onClick={() => setPanel("undo")}>Geri al</Button>
            </div>
          )}
        </div>
      </div>
      {deleteError && <div className="px-4 pb-2"><ErrorBox message={deleteError} /></div>}
      {panel === "collect" && <CollectPanel i={i} label={actionLabel} onClose={() => setPanel(null)} onDone={() => { setPanel(null); onChange(); }} />}
      {panel === "undo" && <UndoPanel i={i} onClose={() => setPanel(null)} onDone={() => { setPanel(null); onChange(); }} />}
      {panel === "edit" && <EditPanel i={i} onClose={() => setPanel(null)} onDone={() => { setPanel(null); onChange(); }} />}
      {panel === "endorse" && <EndorsePanel i={i} onClose={() => setPanel(null)} onDone={() => { setPanel(null); onChange(); }} />}
      {panel === "bounce" && <BouncePanel i={i} onClose={() => setPanel(null)} onDone={() => { setPanel(null); onChange(); }} />}
    </li>
  );
}

function CollectPanel({ i, label, onClose, onDone }: { i: InstallmentResponse; label: string; onClose: () => void; onDone: () => void }) {
  const accounts = useLoad(() => api.accounts(), []);
  const [accountId, setAccountId] = useState("");
  const [date, setDate] = useState(todayIso());
  const [amount, setAmount] = useState(centsToInput(i.amountCents));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const selected = accountId || accounts.data?.[0]?.id || "";
  // Cek parca tahsil edilmez; diger taksitlerde daha az tutar girilirse kalani ayni vadeyle acik kalir
  const partialAllowed = i.method !== "CHEQUE";
  const parsed = tlToCents(amount);
  const cents = parsed != null && !Number.isNaN(parsed) ? parsed : 0;

  const submit = async () => {
    if (partialAllowed && (cents <= 0 || cents > i.amountCents)) return setError(`Tutar en fazla ${tl(i.amountCents)} olabilir.`);
    setBusy(true);
    setError(null);
    try {
      const collected = partialAllowed ? cents : i.amountCents;
      const cashPart = collected - Math.min(i.capitalCents, collected);
      await api.collect(i.id, cashPart > 0 ? selected : null, date, partialAllowed && cents !== i.amountCents ? cents : null);
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
      <div className={`grid gap-3 ${partialAllowed ? "sm:grid-cols-[1fr_150px_160px]" : "sm:grid-cols-[1fr_170px]"}`}>
        {(i.capitalCents === 0 || cents > i.capitalCents) && (
          <Field label={i.direction === "SALE" ? "Para hangi hesaba girdi" : "Para hangi hesaptan çıktı"}>
            {accounts.data && (
              <Segment<string> value={selected} onChange={setAccountId}
                options={accounts.data.map((a) => ({ value: a.id, label: a.name }))} />
            )}
          </Field>
        )}
        {partialAllowed && (
          <Field label="Tutar"><NumberInput value={amount} onValueChange={setAmount} preview={false} /></Field>
        )}
        <Field label="Tarih"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} /></Field>
      </div>
      {cents > 0 && i.capitalCents > 0 && (
        <p className="text-[13px] text-murekkep-2">
          Sermayeden {tl(Math.min(i.capitalCents, cents))}{cents > i.capitalCents ? " · Hesaptan " + tl(cents - i.capitalCents) : ""}
          {partialAllowed && cents < i.amountCents ? " · Kalan " + tl(i.amountCents - cents) + " aynı vadeyle bekleyecek." : ""}
        </p>
      )}
      {partialAllowed && cents > 0 && cents < i.amountCents && i.capitalCents === 0 && (
        <p className="text-[13px] text-murekkep-2">Kalan {tl(i.amountCents - cents)} aynı vadeyle bekleyecek.</p>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="quiet" onClick={onClose}>Vazgeç</Button>
        <Button onClick={submit} disabled={busy || ((partialAllowed ? cents : i.amountCents) > Math.min(i.capitalCents, partialAllowed ? cents : i.amountCents) && !selected)}>
          {busy ? "Kaydediliyor…" : `${tl(partialAllowed && cents > 0 ? cents : i.amountCents)} ${label.toLocaleLowerCase("tr-TR")}`}
        </Button>
      </div>
    </div>
  );
}

function UndoPanel({ i, onClose, onDone }: { i: InstallmentResponse; onClose: () => void; onDone: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.undoInstallment(i.id);
      onDone();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="mx-1 mb-3 rounded-[12px] bg-yuzey-2 border border-cizgi p-3 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
      <span className="text-[14px]">{i.endorsedTo
        ? `Ciro geri alınacak; çek / senet ve ${i.endorsedTo.name} ile olan borç yeniden açılacak.`
        : "Kasadaki kayıt ters hareketle düzeltilecek, taksit yeniden bekliyor olacak."}</span>
      {error && <ErrorBox message={error} />}
      <div className="flex gap-2 shrink-0">
        <Button variant="quiet" onClick={onClose}>Vazgeç</Button>
        <Button variant="danger" onClick={submit} disabled={busy}>Geri al</Button>
      </div>
    </div>
  );
}

function EditPanel({ i, onClose, onDone }: { i: InstallmentResponse; onClose: () => void; onDone: () => void }) {
  const [dueDate, setDueDate] = useState(i.dueDate);
  const [noteNo, setNoteNo] = useState(i.noteNo ?? "");
  const [bankName, setBankName] = useState(i.bankName ?? "");
  const [drawerName, setDrawerName] = useState(i.drawerName ?? "");
  const [error, setError] = useState<string | null>(null);
  const cheque = i.method === "CHEQUE";
  const submit = async () => {
    setError(null);
    try {
      await api.updateInstallment(i.id, dueDate, noteNo.trim() || null, cheque ? bankName.trim() : null, cheque ? drawerName.trim() : null);
      onDone();
    } catch (e) {
      setError(errorMessage(e));
    }
  };
  return (
    <div className="mx-1 mb-3 rounded-[12px] bg-yuzey-2 border border-cizgi p-3 space-y-3">
      <ErrorBox message={error} />
      <div className="grid grid-cols-2 gap-3">
        <Field label="Vade"><input type="date" className={inputClass} value={dueDate} onChange={(e) => setDueDate(e.target.value)} /></Field>
        {i.method !== "OPEN_ACCOUNT" && <Field label={cheque ? "Çek no" : "Senet no"}><input className={inputClass} value={noteNo} onChange={(e) => setNoteNo(e.target.value)} /></Field>}
        {cheque && <>
          <Field label="Banka"><input className={inputClass} value={bankName} onChange={(e) => setBankName(e.target.value)} /></Field>
          <Field label="Keşideci"><input className={inputClass} value={drawerName} onChange={(e) => setDrawerName(e.target.value)} /></Field>
        </>}
      </div>

      <div className="flex justify-end gap-2">
        <Button variant="quiet" onClick={onClose}>Vazgeç</Button>
        <Button onClick={submit}>Kaydet</Button>
      </div>
    </div>
  );
}
