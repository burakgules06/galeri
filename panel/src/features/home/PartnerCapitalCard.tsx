// Sermayedarlar (/api/holders): listeden secilen kisinin parasi halka grafikte nakit / aracta / alacak olarak.
// Dukkan sahibi havuzdan kalandir: kasa + bankalar + araclar + alacaklar - borclar - sermayedarlarin parasi.
import { useState } from "react";
import { api } from "../../api/galeri";
import { Donut } from "../../shared/charts/Donut";
import { percent, tl, tlShort } from "../../shared/format";
import type { Holder } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { Button, ErrorBox } from "../../shared/ui/Common";
import { Plate } from "../../shared/ui/Plate";

const CASH = "var(--color-seri-1)";
const BOUND = "var(--color-seri-4)";
const RECEIVABLE = "var(--color-seri-3)";

const keyOf = (h: Holder) => h.contactId ?? "owner";

export function PartnerCapitalCard({ revision = 0 }: { revision?: number }) {
  const { data, error } = useLoad(() => api.holders(), [revision]);
  const [selected, setSelected] = useState<string | null>(null);
  const [details, setDetails] = useState(false);
  const rows = (data ?? []).filter((h) => h.owner || h.capitalCents !== 0);
  const h = rows.find((r) => keyOf(r) === selected) ?? rows[0];
  const statement = useLoad(() => h?.contactId ? api.statement(h.contactId) : Promise.resolve(null), [h?.contactId]);

  return (
    <section className="kart p-4 md:p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          <p className="etiket shrink-0">Sermayedar</p>
          {rows.length > 0 && (
            <select aria-label="Sermayedar seç" value={h ? keyOf(h) : ""} onChange={(e) => { setSelected(e.target.value); setDetails(false); }}
              className="h-8 max-w-[180px] rounded-lg border border-cizgi-2 bg-yuzey px-2 text-[14px] font-semibold focus:outline-none focus:border-plaka">
              {rows.map((r) => <option key={keyOf(r)} value={keyOf(r)}>{r.name}</option>)}
            </select>
          )}
        </div>
        {h?.owner && <span className="text-[11px] font-semibold text-plaka-koyu bg-plaka-acik rounded px-1.5 py-0.5">Dükkân sahibi</span>}
      </div>
      <ErrorBox message={error} />
      {data && rows.length === 0 && <p className="text-[14px] text-murekkep-3 mt-4">Henüz sermaye girişi yok.</p>}

      {h && (
        <>
          <div className="mt-4 grid gap-5 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
            <div className="min-w-0">
              <div className="flex flex-col sm:flex-row sm:items-center gap-5">
            <div className="mx-auto sm:mx-0">
              <Donut ariaLabel={`${h.name} sermayesinin dağılımı`} size={168} thickness={22}
                slices={[
                  { key: "cash", label: `Nakit ${tl(h.cashCents)}`, value: Math.max(0, h.cashCents), color: CASH },
                  { key: "bound", label: `Araçta ${tl(h.vehicleCents)}`, value: h.vehicleCents, color: BOUND },
                  { key: "other", label: `Alacak ${tl(h.otherCents)}`, value: Math.max(0, h.otherCents), color: RECEIVABLE },
                ].filter((s) => s.value > 0)}
                center={<><span className="sayi font-bold text-[20px] leading-none">{tlShort(h.capitalCents)}</span>
                  <span className="etiket mt-1.5">{percent(h.shareBp)}</span></>} />
            </div>
            <dl className="grow min-w-0 space-y-2 text-[15px]">
              <Line label="Sermaye" value={h.capitalCents} bold />
              <Line label="Nakit" value={h.cashCents} color={CASH} />
              <Line label="Araçta" value={h.vehicleCents} color={BOUND} />
              {h.otherCents !== 0 && <Line label={h.otherCents > 0 ? "Alacak" : "Borç"} value={h.otherCents} color={h.otherCents > 0 ? RECEIVABLE : undefined} />}
              <Button variant="secondary" className="h-9 px-3 text-[14px] mt-1" onClick={() => setDetails((d) => !d)}>
                {details ? "Detayları gizle" : "Detayları gör"}
              </Button>
            </dl>
          </div>

          {details && (
            <ul className="mt-4 border-t border-cizgi divide-y divide-cizgi">
              {h.vehicles.map((x) => (
                <li key={x.vehicle.id}>
                  <a href={`#/vehicles/${x.vehicle.id}`} className="flex items-center gap-3 py-2.5 hover:bg-yuzey-2 -mx-1 px-1 rounded">
                    <Plate plate={x.vehicle.plate} size="sm" />
                    <span className="grow min-w-0 truncate font-semibold text-[14px]">{x.vehicle.make} <span className="font-medium text-murekkep-2">{x.vehicle.model}</span></span>
                    <span className="sayi text-[15px]">{tl(x.cents)}</span>
                  </a>
                </li>
              ))}
              <li className="flex items-center gap-3 py-2.5">
                <span className="w-3 h-3 rounded-[4px] shrink-0" style={{ background: CASH }} />
                <span className="grow font-semibold text-[14px]">Nakit</span>
                <span className={`sayi text-[15px] ${h.cashCents < 0 ? "text-tehlike" : ""}`}>{tl(h.cashCents)}</span>
              </li>
            </ul>
          )}

              <div className="mt-4 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-murekkep-2">
                <Legend color={CASH} label="Nakit (kasa ve bankalardaki payı)" />
                <Legend color={BOUND} label="Araçta (araçlara bağlı parası)" />
                <Legend color={RECEIVABLE} label="Alacak (senet, çek ve açık alacaklar)" />
              </div>
            </div>
            <div className="min-w-0 border-t border-cizgi pt-4 lg:border-t-0 lg:border-l lg:pt-0 lg:pl-5">
              <p className="etiket">Alacak ve verecek</p>
              {!h.contactId
                ? <p className="mt-3 text-[14px] text-murekkep-3">Cari hesabı yok.</p>
                : statement.loading
                  ? <p className="mt-3 text-[14px] text-murekkep-3">Yükleniyor…</p>
                  : statement.error
                    ? <ErrorBox message={statement.error} />
                    : statement.data && (
                      <>
                        <div className="grid grid-cols-2 gap-4 mt-4">
                          <div className="min-w-0">
                            <p className="text-[13px] font-semibold text-murekkep-2">Alacak</p>
                            <p className="sayi font-bold text-[clamp(1.25rem,2.4vw,1.75rem)] leading-tight text-basari mt-1 break-words">{tl(Math.max(0, -statement.data.balanceCents) + Math.max(0, h.otherCents))}</p>
                          </div>
                          <div className="min-w-0 border-l border-cizgi pl-4">
                            <p className="text-[13px] font-semibold text-murekkep-2">Verecek</p>
                            <p className="sayi font-bold text-[clamp(1.25rem,2.4vw,1.75rem)] leading-tight text-tehlike mt-1 break-words">{tl(Math.max(0, statement.data.balanceCents))}</p>
                          </div>
                        </div>
                        <a href={`#/contacts/${h.contactId}`} className="inline-block mt-4 text-sm font-semibold text-plaka hover:text-plaka-koyu">Cari detayını gör →</a>
                      </>
                    )}
            </div>
          </div>
        </>
      )}
    </section>
  );
}

function Line({ label, value, color, bold }: { label: string; value: number; color?: string; bold?: boolean }) {
  return (
    <div className="flex items-center gap-2">
      {color ? <span className="w-3 h-3 rounded-[4px] shrink-0" style={{ background: color }} /> : <span className="w-3 shrink-0" />}
      <dt className="text-murekkep-2">{label}</dt>
      <dd className={`ml-auto whitespace-nowrap sayi ${bold ? "font-bold text-[18px]" : "text-[16px]"} ${value < 0 ? "text-tehlike" : ""}`}>{tl(value)}</dd>
    </div>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-[3px] shrink-0" style={{ background: color }} />{label}</span>;
}

