// Defter hareketleri. Hareket silinmez: "Düzelt" ayni tutarda ters hareket atar, asil satir gri ve ustu cizili kalir.
import { useState } from "react";
import { api } from "../../api/galeri";
import { ENTRY_TYPE, shortDate, tl } from "../../shared/format";
import type { LedgerEntryResponse } from "../../shared/types";
import { errorMessage } from "../../shared/useLoad";
import { Plate } from "../../shared/ui/Plate";
import { Button, ErrorBox } from "../../shared/ui/Common";

export function LedgerEntryList({ entries, direction, onChange, showVehicle = true }: {
  entries: LedgerEntryResponse[];
  /** +1 giris (yesil), -1 cikis (kirmizi), 0 notr */
  direction: (e: LedgerEntryResponse) => number;
  onChange: () => void;
  showVehicle?: boolean;
}) {
  return (
    <ul className="divide-y divide-cizgi">
      {entries.map((e) => <Row key={e.id} e={e} direction={direction(e)} onChange={onChange} showVehicle={showVehicle} />)}
    </ul>
  );
}

function Row({ e, direction, onChange, showVehicle }: { e: LedgerEntryResponse; direction: number; onChange: () => void; showVehicle: boolean }) {
  const [confirming, setConfirming] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isReversal = e.correctedEntryId != null;
  const cancelled = e.corrected;
  const title = e.category ?? (e.type === "CAPITAL" ? `Sermaye · ${e.source.name}` : ENTRY_TYPE[e.type]);

  const correct = async () => {
    setWorking(true);
    setError(null);
    try {
      await api.correctEntry(e.id);
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
            {shortDate(e.date)} · {e.source.name} → {e.target.name}
          </p>
          {e.description && e.description !== title && e.description !== "Düzeltme" && <p className="text-[14px] text-murekkep-3 mt-0.5">{e.description}</p>}
        </div>
        <div className="text-right shrink-0">
          <p className={`font-cond font-semibold text-[19px] whitespace-nowrap ${amountColor} ${cancelled ? "line-through" : ""}`}>
            {sign}{sign && " "}{tl(e.amountCents)}
          </p>
          {!isReversal && !cancelled && !confirming && (
            <button type="button" onClick={() => setConfirming(true)} className="text-[13px] font-semibold text-murekkep-2 hover:text-murekkep">
              Düzelt
            </button>
          )}
        </div>
      </div>
      {confirming && (
        <div className="mt-2 rounded-lg bg-yuzey-2 border border-cizgi px-3 py-2 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <span className="text-[14px]">Aynı tutarda ters hareket atılacak, bu kayıt iptal görünecek.</span>
          <div className="flex gap-2 shrink-0">
            <Button variant="quiet" className="h-9" onClick={() => setConfirming(false)}>Vazgeç</Button>
            <Button variant="danger" className="h-9" onClick={correct} disabled={working}>Düzelt</Button>
          </div>
        </div>
      )}
      {error && <div className="mt-2"><ErrorBox message={error} /></div>}
    </li>
  );
}

function Tag({ children }: { children: string }) {
  return <span className="text-[12px] font-semibold px-1.5 py-0.5 rounded bg-yuzey-2 border border-cizgi text-murekkep-2">{children}</span>;
}
