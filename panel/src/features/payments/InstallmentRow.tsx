// Taksit satiri: odeyen, (istenirse) arac, vade + durum, tutar, yontem. Islemler satirin altinda acilir:
// tahsil et (hesap + tarih), geri al (onayla), vade / senet no duzenle. Durum renkleri: odendi yesil, bekliyor turuncu, gecikti kirmizi.
import { useState } from "react";
import { api } from "../../api/galeri";
import { dueLabel, formatDate, formatPhone, METHOD, tl, todayIso } from "../../shared/format";
import type { InstallmentResponse, InstallmentStatus } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Segment } from "../../shared/ui/Common";
import { Plate } from "../../shared/ui/Plate";

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

type Panel = "collect" | "undo" | "edit" | null;

export function InstallmentRow({ i, onChange, showVehicle = true, showPayer = true }: {
  i: InstallmentResponse;
  onChange: () => void;
  showVehicle?: boolean;
  showPayer?: boolean;
}) {
  const [panel, setPanel] = useState<Panel>(null);
  const paid = i.status === "PAID";
  const actionLabel = i.direction === "SALE" ? "Tahsil et" : "Öde";

  return (
    <li className="relative">
      <span className={`absolute left-0 top-3 bottom-3 w-[3px] rounded-r ${STRIPE[i.status]}`} />
      <div className="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-4 pl-4 pr-1 py-3">
        <div className="min-w-0 grow">
          <div className="flex items-center gap-2 flex-wrap">
            {showPayer && <span className="font-semibold text-[15.5px] truncate">{i.payer.name}</span>}
            {!showPayer && <span className="font-semibold text-[15px]">{i.seq}. taksit</span>}
            <span className={`text-[12px] font-semibold rounded-full px-2 py-0.5 ${PILL[i.status]}`}>{dueLabel(i)}</span>
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
            <span>{METHOD[i.method]}{i.noteNo ? ` no ${i.noteNo}` : ""}</span>
            {showPayer && <><span className="text-murekkep-3">·</span><span>{i.seq}/{i.count}</span></>}
            {showPayer && i.payer.phone && <span className="sayi text-[14px] text-murekkep-3">{formatPhone(i.payer.phone)}</span>}
          </div>
        </div>
        <div className="flex items-center justify-between sm:justify-end gap-3 shrink-0">
          <span className={`sayi font-bold text-[21px] ${paid ? "text-basari" : i.status === "OVERDUE" ? "text-tehlike" : "text-murekkep"}`}>
            {tl(i.amountCents)}
          </span>
          {!paid && panel === null && (
            <div className="flex gap-1.5">
              <Button variant="quiet" className="h-9 px-2.5" onClick={() => setPanel("edit")} aria-label="Vadeyi düzenle">Düzenle</Button>
              <Button className="h-9" onClick={() => setPanel("collect")}>{actionLabel}</Button>
            </div>
          )}
          {paid && panel === null && (
            <Button variant="quiet" className="h-9" onClick={() => setPanel("undo")}>Geri al</Button>
          )}
        </div>
      </div>
      {panel === "collect" && <CollectPanel i={i} label={actionLabel} onClose={() => setPanel(null)} onDone={() => { setPanel(null); onChange(); }} />}
      {panel === "undo" && <UndoPanel i={i} onClose={() => setPanel(null)} onDone={() => { setPanel(null); onChange(); }} />}
      {panel === "edit" && <EditPanel i={i} onClose={() => setPanel(null)} onDone={() => { setPanel(null); onChange(); }} />}
    </li>
  );
}

function CollectPanel({ i, label, onClose, onDone }: { i: InstallmentResponse; label: string; onClose: () => void; onDone: () => void }) {
  const accounts = useLoad(() => api.accounts(), []);
  const [accountId, setAccountId] = useState("");
  const [date, setDate] = useState(todayIso());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const selected = accountId || accounts.data?.[0]?.id || "";

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.collect(i.id, selected, date);
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
        <Field label={i.direction === "SALE" ? "Para hangi hesaba girdi" : "Para hangi hesaptan çıktı"}>
          {accounts.data && (
            <Segment<string> value={selected} onChange={setAccountId}
              options={accounts.data.map((a) => ({ value: a.id, label: a.name }))} />
          )}
        </Field>
        <Field label="Tarih"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} /></Field>
      </div>
      <div className="flex justify-end gap-2">
        <Button variant="quiet" onClick={onClose}>Vazgeç</Button>
        <Button onClick={submit} disabled={busy || !selected}>{busy ? "Kaydediliyor…" : `${tl(i.amountCents)} ${label.toLocaleLowerCase("tr-TR")}`}</Button>
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
      <span className="text-[14px]">Kasadaki kayıt ters hareketle düzeltilecek, taksit yeniden bekliyor olacak.</span>
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
  const [error, setError] = useState<string | null>(null);
  const submit = async () => {
    setError(null);
    try {
      await api.updateInstallment(i.id, dueDate, noteNo.trim() || null);
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
        <Field label="Senet no"><input className={inputClass} value={noteNo} onChange={(e) => setNoteNo(e.target.value)} placeholder="İsteğe bağlı" /></Field>
      </div>

      <div className="flex justify-end gap-2">
        <Button variant="quiet" onClick={onClose}>Vazgeç</Button>
        <Button onClick={submit}>Kaydet</Button>
      </div>
    </div>
  );
}
