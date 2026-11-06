import { useState } from "react";
import { api } from "../../api/galeri";
import { formatDate, METHOD, tl } from "../../shared/format";
import { useLoad } from "../../shared/useLoad";
import { Button, inputClass } from "../../shared/ui/Common";
import { endorsable, endorsedTotal, type EndorsedRow } from "./endorsedRows";

/**
 * Arac alisi: "Elimdeki çek / senetle ödenen". Portfoyden bir ya da birden fazla evrak secilir; her biri satıcıya
 * ayni tutarda ciro edilir (evrakin sahibinin borcu kapanir, saticiya olan borc duser, kasa degismez).
 */
export function EndorsedInstruments({ rows, onChange, sellerId, sellerName }: {
  rows: EndorsedRow[];
  onChange: (rows: EndorsedRow[]) => void;
  sellerId: string | null;
  sellerName: string | null;
}) {
  const portfolio = useLoad(() => api.installments({ direction: "SALE" }), []);
  const [picking, setPicking] = useState(false);
  const [checked, setChecked] = useState<string[]>([]);
  const options = endorsable(portfolio.data, sellerId, rows);

  const add = () => {
    const chosen = options.filter((o) => checked.includes(o.id)).map((instrument) => ({ instrument, description: "" }));
    onChange([...rows, ...chosen]);
    setChecked([]);
    setPicking(false);
  };

  return (
    <div className="rounded-[12px] border border-cizgi p-3 space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="m-0 text-[14px] font-semibold">Elimdeki çek / senetle ödenen</p>
          <p className="m-0 text-[13px] text-murekkep-3">
            {sellerName ? `Seçilen evraklar ${sellerName} adına ciro edilir; kasa değişmez.` : "Önce kimden alındığını seç."}
          </p>
        </div>
        {!picking && <Button variant="secondary" className="h-9" disabled={!sellerId} onClick={() => setPicking(true)}>+ Çek / senet ekle</Button>}
      </div>

      {rows.map((r, k) => (
        <div key={r.instrument.id} className="rounded-[10px] bg-yuzey-2 px-2.5 py-2">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div className="min-w-0">
              <p className="m-0 text-[14px] font-semibold">{METHOD[r.instrument.method]}{r.instrument.noteNo ? ` · ${r.instrument.noteNo}` : ""}</p>
              <p className="m-0 text-[13px] text-murekkep-3">
                {r.instrument.payer.name} · vade {formatDate(r.instrument.dueDate)}{r.instrument.bankName ? ` · ${r.instrument.bankName}` : ""}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <b className="sayi text-[16px]">{tl(r.instrument.amountCents)}</b>
              <button type="button" aria-label="Çıkar" onClick={() => onChange(rows.filter((_, j) => j !== k))}
                className="h-8 w-8 rounded-full border border-cizgi bg-yuzey text-murekkep-2 cursor-pointer">×</button>
            </div>
          </div>
          <input className={`${inputClass} h-9 w-full mt-2`} value={r.description} maxLength={500} placeholder="Açıklama (isteğe bağlı)"
            aria-label="Açıklama" onChange={(e) => onChange(rows.map((x, j) => (j === k ? { ...x, description: e.target.value } : x)))} />
        </div>
      ))}
      {rows.length > 1 && <p className="m-0 text-right text-[13px] text-murekkep-2">Evraklarla ödenen: <b className="sayi">{tl(endorsedTotal(rows))}</b></p>}

      {picking && (
        <div className="rounded-[10px] border border-cizgi p-2 space-y-1.5">
          {portfolio.data && options.length === 0 && <p className="m-0 text-[14px] text-murekkep-3">Elde ciro edilebilecek çek ya da senet yok.</p>}
          <div className="max-h-72 overflow-y-auto space-y-1.5">
            {options.map((o) => (
              <label key={o.id} className={`flex items-start gap-2 rounded-[10px] border px-2.5 py-2 text-[14px] cursor-pointer ${checked.includes(o.id) ? "border-plaka bg-yuzey" : "border-cizgi"}`}>
                <input type="checkbox" className="accent-plaka mt-1" checked={checked.includes(o.id)}
                  onChange={(e) => setChecked((c) => (e.target.checked ? [...c, o.id] : c.filter((x) => x !== o.id)))} />
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-baseline justify-between gap-x-3">
                    <span className="font-semibold">{METHOD[o.method]}{o.noteNo ? ` · ${o.noteNo}` : ""}</span>
                    <b className="sayi">{tl(o.amountCents)}</b>
                  </span>
                  <span className="block text-[13px] text-murekkep-3">
                    {o.payer.name} · vade {formatDate(o.dueDate)}{o.bankName ? ` · ${o.bankName}` : ""}{o.vehicle?.plate ? ` · ${o.vehicle.plate}` : ""}
                  </span>
                </span>
              </label>
            ))}
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="quiet" onClick={() => { setPicking(false); setChecked([]); }}>Vazgeç</Button>
            <Button onClick={add} disabled={checked.length === 0}>Ekle</Button>
          </div>
        </div>
      )}
    </div>
  );
}
