// Defter hareketleri. Hareket silinmez: masrafta "Düzenle" eski kaydi tersleyip yenisini yazar, "Sil" ters kayit atar;
// diger hareketlerde "Düzelt" ayni tutarda ters hareket atar. Duzeltilmis kayit + duzeltmesi varsayilan olarak gizli,
// "Düzeltmeleri göster" ile gri ve ustu cizili gorunur. Maliyet karti ve Kasa listesi bu bileseni kullanir.
import { useState } from "react";
import { api } from "../../api/galeri";
import { ENTRY_TYPE, formatDate, shortDateTime, tl } from "../../shared/format";
import type { LedgerEntryResponse, LedgerEntryType } from "../../shared/types";
import { errorMessage } from "../../shared/useLoad";
import { Plate } from "../../shared/ui/Plate";
import { Button, ErrorBox } from "../../shared/ui/Common";
import { ExpenseForm } from "./ExpenseForm";
import { groupEntries, groupTotal } from "./entryGroups";

// Satis ve taksit hareketleri plana bagli: arac detayindan / odemeler ekranindan geri alinir, burada duzeltilmez.
const LINKED: LedgerEntryType[] = ["VEHICLE_SALE", "COLLECTION", "PAYMENT", "CONSIGNMENT_SETTLEMENT", "PARTNER_SALE_SETTLEMENT"];
const EXPENSE: LedgerEntryType[] = ["VEHICLE_EXPENSE", "GENERAL_EXPENSE"];

export function LedgerEntryList({ entries, direction, onChange, showVehicle = true, corrections, readOnly = false, grouped = false, balances = false }: {
  entries: LedgerEntryResponse[];
  /** +1 giris (yesil), -1 cikis (kirmizi), 0 notr */
  direction: (e: LedgerEntryResponse) => number;
  onChange: () => void;
  showVehicle?: boolean;
  /** Kasa ekrani: "hide" duzeltmeleri hic gostermez, "only" sadece duzeltilen + duzeltmeleri. Bos: "Düzeltmeleri göster" kutusu. */
  corrections?: "hide" | "only";
  /** Sermayedar: duzenle / sil / duzelt yok */
  readOnly?: boolean;
  /** Ayni araca / kisiye ayni gun yapilan hareketler tek satirda toplam (entryGroups) */
  grouped?: boolean;
  /** Kasa ekrani: her satirda islem sonrasi kasa / banka bakiyesi (hareket yazildigi anin fotografi) */
  balances?: boolean;
}) {
  const [showCorrections, setShowCorrections] = useState(false);
  const isCorrection = (e: LedgerEntryResponse) => e.corrected || e.correctedEntryId != null;
  const hidden = corrections ? 0 : entries.filter(isCorrection).length;
  const visible = corrections === "only" ? entries.filter(isCorrection)
    : corrections === "hide" || !showCorrections ? entries.filter((e) => !isCorrection(e)) : entries;

  return (
    <div>
      {hidden > 0 && (
        <label className="mb-2 inline-flex items-center gap-2 text-[13px] font-semibold text-murekkep-2 cursor-pointer select-none">
          <input type="checkbox" className="accent-plaka w-4 h-4" checked={showCorrections} onChange={(e) => setShowCorrections(e.target.checked)} />
          Düzeltmeleri göster ({hidden})
        </label>
      )}
      {visible.length === 0
        ? <p className="text-[14px] text-murekkep-3">Gösterilecek kayıt yok.</p>
        : (
          <ul className="divide-y divide-cizgi">
            {(grouped ? groupEntries(visible) : visible.map((e) => [e])).map((g) => g.length === 1
              ? <Row key={g[0].id} e={g[0]} direction={direction(g[0])} onChange={onChange} showVehicle={showVehicle} readOnly={readOnly} balances={balances} />
              : <GroupRow key={g[0].id} entries={g} direction={direction} onChange={onChange} showVehicle={showVehicle} readOnly={readOnly} balances={balances} />)}
          </ul>
        )}
    </div>
  );
}

function Row({ e, direction, onChange, showVehicle, readOnly, balances = false }: {
  e: LedgerEntryResponse; direction: number; onChange: () => void; showVehicle: boolean; readOnly: boolean; balances?: boolean;
}) {
  const [confirming, setConfirming] = useState(false);
  const [editing, setEditing] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isReversal = e.correctedEntryId != null;
  const cancelled = e.corrected;
  const active = !readOnly && !isReversal && !cancelled;
  const expense = EXPENSE.includes(e.type);
  const correctable = active && !expense && !LINKED.includes(e.type) && !(e.type === "VEHICLE_PURCHASE" && e.source.type === "BALANCE");
  const title = e.category ?? (e.type === "CAPITAL" ? `Sermaye · ${e.source.name}` : ENTRY_TYPE[e.type]);

  const reverse = async () => {
    setWorking(true);
    setError(null);
    try {
      await (expense ? api.deleteExpense(e.id) : api.correctEntry(e.id));
      setConfirming(false);
      onChange();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setWorking(false);
    }
  };

  const amountColor = cancelled || isReversal ? "text-murekkep-3" : direction > 0 ? "text-basari" : direction < 0 ? "text-tehlike" : "text-murekkep";
  const sign = direction > 0 ? "+" : direction < 0 ? "−" : "";

  return (
    <li className="py-3 first:pt-0 last:pb-0">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className={`font-semibold text-[16px] ${cancelled ? "line-through text-murekkep-3" : ""}`}>{title}</span>
            {isReversal && <Tag>Düzeltme</Tag>}
            {cancelled && <Tag>Düzeltildi</Tag>}
          </div>
          {showVehicle && e.vehicle && (
            <a href={`#/vehicles/${e.vehicle.id}`} className="mt-1 flex items-center gap-2 text-[14px] text-murekkep-2 hover:text-murekkep">
              <Plate plate={e.vehicle.plate} size="sm" />
              <span className="truncate">{e.vehicle.make} {e.vehicle.model}</span>
            </a>
          )}
          <p className="text-[14px] text-murekkep-2 mt-0.5">
            {e.source.type === "CAPITAL" && EXPENSE.includes(e.type) ? `Cepten (${e.source.name})` : e.source.name} → {e.target.name}
            {e.createdBy && <span className="text-murekkep-3"> · {e.createdBy}</span>}
          </p>
          {e.description && e.description !== title && e.description !== "Düzeltme" && <p className="text-[14px] text-murekkep-3 mt-0.5">{e.description}</p>}
        </div>
        <div className="text-right shrink-0">
          <p className={`font-cond font-semibold text-[19px] whitespace-nowrap ${amountColor} ${cancelled ? "line-through" : ""}`}>
            {sign}{sign && " "}{tl(e.amountCents)}
          </p>
          <p className="text-[12.5px] text-murekkep-2 whitespace-nowrap" title={`Kayıt: ${shortDateTime(e.createdAt)}`}>
            {formatDate(e.date)}{e.createdAt && <span className="text-murekkep-3"> · {timeOf(e.createdAt)}</span>}
          </p>
          {balances && <BalanceAfter e={e} />}
          {active && expense && !confirming && (
            <span className="inline-flex gap-3">
              <button type="button" onClick={() => setEditing(true)} className="text-[13px] font-semibold text-plaka hover:underline">Düzenle</button>
              <button type="button" onClick={() => setConfirming(true)} className="text-[13px] font-semibold text-tehlike hover:underline">Sil</button>
            </span>
          )}
          {correctable && !confirming && (
            <button type="button" onClick={() => setConfirming(true)} className="text-[13px] font-semibold text-murekkep-2 hover:text-murekkep">
              Düzelt
            </button>
          )}
        </div>
      </div>
      {confirming && (
        <div className="mt-2 rounded-lg bg-yuzey-2 border border-cizgi px-3 py-2 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <span className="text-[14px]">
            {expense ? `Masraf silinecek, ${tl(e.amountCents)} ${e.source.name} hesabına geri eklenecek.` : "Aynı tutarda ters hareket atılacak, bu kayıt iptal görünecek."}
          </span>
          <div className="flex gap-2 shrink-0">
            <Button variant="quiet" className="h-9" onClick={() => setConfirming(false)}>Vazgeç</Button>
            <Button variant="danger" className="h-9" onClick={reverse} disabled={working}>{expense ? "Sil" : "Düzelt"}</Button>
          </div>
        </div>
      )}
      {error && <div className="mt-2"><ErrorBox message={error} /></div>}
      <ExpenseForm open={editing} existing={e} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); onChange(); }} />
    </li>
  );
}

function GroupRow({ entries, direction, onChange, showVehicle, readOnly, balances = false }: {
  entries: LedgerEntryResponse[]; direction: (e: LedgerEntryResponse) => number; onChange: () => void; showVehicle: boolean; readOnly: boolean;
  balances?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const e = entries[0];
  const { cents: total, dir, mixed } = groupTotal(entries, direction);
  const amountColor = dir > 0 ? "text-basari" : dir < 0 ? "text-tehlike" : "text-murekkep";
  const sign = dir > 0 ? "+" : dir < 0 ? "−" : "";
  const person = e.vehicle ? null : (e.source.type === "BALANCE" || e.source.type === "CAPITAL" ? e.source : e.target).name;
  const latest = entries.reduce((m, x) => ((x.createdAt ?? "") > (m.createdAt ?? "") ? x : m), e);

  return (
    <li className="py-3 first:pt-0 last:pb-0">
      <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open}
        className="basilir w-full text-left flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-semibold text-[16px]">{ENTRY_TYPE[e.type]}{person ? ` · ${person}` : ""}</span>
            <Tag>{`${entries.length} işlem`}</Tag>
          </div>
          {showVehicle && e.vehicle && (
            <span className="mt-1 flex items-center gap-2 text-[14px] text-murekkep-2">
              <Plate plate={e.vehicle.plate} size="sm" />
              <span className="truncate">{e.vehicle.make} {e.vehicle.model}</span>
            </span>
          )}
          <p className="text-[14px] text-murekkep-2 mt-0.5">
            {e.source.type === "CAPITAL" && EXPENSE.includes(e.type) ? `Cepten (${e.source.name})` : e.source.name} {mixed ? "↔" : "→"} {e.target.name}
            <span className="text-plaka font-semibold"> · {open ? "Detayı gizle" : "Detayı gör"}</span>
          </p>
        </div>
        <div className="text-right shrink-0">
          <p className={`font-cond font-semibold text-[19px] whitespace-nowrap ${amountColor}`}>{sign}{sign && " "}{tl(total)}</p>
          <p className="text-[12.5px] text-murekkep-2 whitespace-nowrap">
            {formatDate(e.date)}{latest.createdAt && <span className="text-murekkep-3"> · {timeOf(latest.createdAt)}</span>}
          </p>
          {balances && <BalanceAfter e={latest} />}
        </div>
      </button>
      {open && (
        <ul className="mt-3 ml-3 pl-3 border-l-2 border-cizgi divide-y divide-cizgi">
          {entries.map((x) => <Row key={x.id} e={x} direction={direction(x)} onChange={onChange} showVehicle={false} readOnly={readOnly} balances={balances} />)}
        </ul>
      )}
    </li>
  );
}

/** Islem sonrasi kasa / banka bakiyesi: "Kasa: 153.400 ₺"; virmanda iki hesap alt alta */
function BalanceAfter({ e }: { e: LedgerEntryResponse }) {
  const items = [
    e.sourceBalanceAfterCents != null ? { name: e.source.name, cents: e.sourceBalanceAfterCents } : null,
    e.targetBalanceAfterCents != null ? { name: e.target.name, cents: e.targetBalanceAfterCents } : null,
  ].filter((x): x is { name: string; cents: number } => x != null);
  if (items.length === 0) return null;
  return (
    <>
      {items.map((x) => (
        <p key={x.name} className="text-[12.5px] text-murekkep-3 whitespace-nowrap" title="İşlem sonrası bakiye">
          {x.name}: <span className={`font-cond font-semibold ${x.cents < 0 ? "text-tehlike" : "text-murekkep-2"}`}>{tl(x.cents)}</span>
        </p>
      ))}
    </>
  );
}

/** Kaydin saati (yerel), 14:05 */
function timeOf(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "" : new Intl.DateTimeFormat("tr-TR", { hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(d);
}

function Tag({ children }: { children: string }) {
  return <span className="text-[12px] font-semibold px-1.5 py-0.5 rounded bg-yuzey-2 border border-cizgi text-murekkep-2">{children}</span>;
}
