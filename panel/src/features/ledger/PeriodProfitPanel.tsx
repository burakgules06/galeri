// Donem Havuz kari (MIMARI §5): galeri araci kari + ortak aractaki Havuz payi + konsinye komisyonu - genel giderler.
// Genel donem kari sermayeye otomatik dagitilmaz; ortak aracin ortak payi satis aninda otomatik sermayeye yazilir.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { addDaysIso, formatDate, percent, shortDate, tl, tlToCents, todayIso, centsToInput } from "../../shared/format";
import type { PartnerProfit, PeriodProfit } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { Plate } from "../../shared/ui/Plate";
import { StatTile } from "../../shared/ui/StatTile";
import { OwnershipBadge } from "../vehicles/OwnershipBadge";
import { NumberInput } from "../../shared/ui/NumberInput";

type Preset = "month" | "lastMonth" | "quarter" | "year" | "custom";

function range(preset: Exclude<Preset, "custom">): [string, string] {
  const today = todayIso();
  const [y, m] = today.split("-").map(Number);
  const iso = (yy: number, mm: number, dd: number) => `${yy}-${String(mm).padStart(2, "0")}-${String(dd).padStart(2, "0")}`;
  const monthEnd = (yy: number, mm: number) => addDaysIso(mm === 12 ? iso(yy + 1, 1, 1) : iso(yy, mm + 1, 1), -1);
  switch (preset) {
    case "month": return [iso(y, m, 1), monthEnd(y, m)];
    case "lastMonth": return m === 1 ? [iso(y - 1, 12, 1), monthEnd(y - 1, 12)] : [iso(y, m - 1, 1), monthEnd(y, m - 1)];
    case "quarter": {
      const q = Math.floor((m - 1) / 3) * 3 + 1;
      return [iso(y, q, 1), monthEnd(y, q + 2)];
    }
    case "year": return [iso(y, 1, 1), iso(y, 12, 31)];
  }
}

export function PeriodProfitPanel({ onChange }: { onChange: () => void }) {
  const [preset, setPreset] = useState<Preset>("year");
  const [[from, to], setPeriod] = useState<[string, string]>(() => range("year"));
  const { data, error, reload } = useLoad(() => api.periodProfit(from, to), [from, to]);
  const [allocating, setAllocating] = useState<PartnerProfit | null>(null);

  const choose = (p: Preset) => {
    setPreset(p);
    if (p !== "custom") setPeriod(range(p));
  };

  return (
    <div className="space-y-4">
      <section className="kart p-4 md:p-5 flex flex-wrap items-end gap-3">
        <Field label="Dönem">
          <Segment<Preset> value={preset} onChange={choose} options={[
            { value: "month", label: "Bu ay" }, { value: "lastMonth", label: "Geçen ay" }, { value: "quarter", label: "Bu çeyrek" },
            { value: "year", label: "Bu yıl" }, { value: "custom", label: "Tarih seç" },
          ]} />
        </Field>
        {preset === "custom" && (
          <div className="flex items-end gap-2">
            <Field label="Başlangıç"><input type="date" className={inputClass} value={from} onChange={(e) => setPeriod([e.target.value, to])} /></Field>
            <Field label="Bitiş"><input type="date" className={inputClass} value={to} onChange={(e) => setPeriod([from, e.target.value])} /></Field>
          </div>
        )}
        <p className="text-[13px] text-murekkep-2 ml-auto">{formatDate(from)} – {formatDate(to)}</p>
      </section>

      <ErrorBox message={error} />
      {data && <ProfitSummary p={data} />}
      {data && <SoldList p={data} />}
      {data && <PartnersTable p={data} onAllocate={setAllocating} />}

      {allocating && data && (
        <AllocationForm partner={allocating} period={data} onClose={() => setAllocating(null)}
          onSaved={() => { setAllocating(null); reload(); onChange(); }} />
      )}
    </div>
  );
}

function ProfitSummary({ p }: { p: PeriodProfit }) {
  return (
    <>
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <StatTile label="Araç kârı" value={tl(p.vehicleProfitCents)} tone={p.vehicleProfitCents >= 0 ? "positive" : "negative"}
          sub={`${p.vehicles.filter((v) => v.ownership === "DEALER").length} galeri aracı · ${p.vehicles.filter((v) => v.ownership === "CO_OWNED").length} ortak araç Havuz payı`} />
        {/* Komisyon ayri satir: sermaye karina girip girmeyecegi acik soru (MIMARI §13) */}
        <StatTile label="Konsinye komisyonu" value={tl(p.commissionCents)} tone={p.commissionCents > 0 ? "positive" : "neutral"}
          sub={`${p.vehicles.filter((v) => v.ownership === "CONSIGNMENT").length} konsinye satış`} />
        <StatTile label="Genel giderler" value={tl(p.generalExpenseCents)} tone={p.generalExpenseCents > 0 ? "negative" : "neutral"}
          />
        <StatTile label={p.netCents >= 0 ? "Net kâr" : "Net zarar"} value={tl(Math.abs(p.netCents))} tone={p.netCents >= 0 ? "positive" : "negative"}
          />
      </div>

    </>
  );
}

function SoldList({ p }: { p: PeriodProfit }) {
  return (
    <section className="kart overflow-hidden">
      <h2 className="font-cond font-bold text-[21px] tracking-tight px-4 md:px-5 pt-4 pb-3 border-b border-cizgi">Dönemde satılan araçlar</h2>
      {p.vehicles.length === 0
        ? <Empty title="Bu dönemde satış yok" />
        : (
          <ul className="divide-y divide-cizgi">
            {p.vehicles.map((v) => (
              <li key={v.vehicle.id}>
                <a href={`#/vehicles/${v.vehicle.id}`} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 md:px-5 py-3 hover:bg-yuzey-2">
                  <span className="w-14 shrink-0 text-[13px] font-medium text-murekkep-2">{shortDate(v.saleDate)}</span>
                  <Plate plate={v.vehicle.plate} size="sm" />
                  <span className="grow min-w-0 truncate font-semibold">{v.vehicle.make} <span className="font-medium text-murekkep-2">{v.vehicle.model}</span></span>
                  <OwnershipBadge ownership={v.ownership} />
                  <span className="text-right w-36">
                    <span className={`block sayi font-semibold text-[17px] ${v.profitCents >= 0 ? "text-basari" : "text-tehlike"}`}>
                      {v.profitCents < 0 ? "−" : ""}{tl(Math.abs(v.profitCents))}
                    </span>
                    <span className="block text-[12px] text-murekkep-3">
                      {v.ownership === "CONSIGNMENT" ? "komisyon" : `satış ${tl(v.salePriceCents)}`}
                    </span>
                  </span>
                </a>
              </li>
            ))}
          </ul>
        )}
    </section>
  );
}

function PartnersTable({ p, onAllocate }: { p: PeriodProfit; onAllocate: (x: PartnerProfit) => void }) {
  return (
    <section className="kart overflow-hidden">
      <div className="px-4 md:px-5 pt-4 pb-3 border-b border-cizgi">
        <h2 className="font-cond font-bold text-[21px] tracking-tight">Ortakların payı</h2>

      </div>
      {p.partners.length === 0
        ? <Empty title="Sermaye ortağı yok" description="Ortaklar sekmesinden ortak ekleyin." />
        : (
          <ul className="divide-y divide-cizgi">
            {p.partners.map((x) => {
              const remaining = x.suggestedCents - x.allocatedCents;
              return (
                <li key={x.contactId} className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 md:px-5 py-3">
                  <span className="grow min-w-0 basis-40">
                    <span className="font-semibold">{x.name}</span>
                    {x.owner && <span className="ml-2 text-[12px] font-semibold text-plaka bg-plaka-acik rounded px-1.5 py-0.5">Dükkân sahibi</span>}
                    <span className="block text-[13px] text-murekkep-2">Sermaye {tl(x.capitalCents)} · {percent(x.shareBp)}</span>
                  </span>
                  <span className="text-right">
                    <span className="etiket block">Önerilen</span>
                    <span className={`sayi font-bold text-[18px] ${x.suggestedCents >= 0 ? "text-murekkep" : "text-tehlike"}`}>{tl(x.suggestedCents)}</span>
                  </span>
                  <span className="text-right">
                    <span className="etiket block">Eklenen</span>
                    <span className="sayi font-semibold text-[18px] text-basari">{tl(x.allocatedCents)}</span>
                  </span>
                  <Button variant={remaining > 0 ? "primary" : "secondary"} className="h-9 px-3 text-[14px]" onClick={() => onAllocate(x)}>Sermayeye ekle</Button>
                </li>
              );
            })}
          </ul>
        )}
    </section>
  );
}

function AllocationForm({ partner, period, onClose, onSaved }: {
  partner: PartnerProfit; period: PeriodProfit; onClose: () => void; onSaved: () => void;
}) {
  const suggestion = Math.max(0, partner.suggestedCents - partner.allocatedCents);
  const [amount, setAmount] = useState(suggestion > 0 ? centsToInput(suggestion) : "");
  const [date, setDate] = useState(period.to < todayIso() ? period.to : todayIso());
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const cents = tlToCents(amount);
    if (cents == null || Number.isNaN(cents) || cents <= 0) return setError("Tutarı 250.000 gibi yaz.");
    setSaving(true);
    setError(null);
    try {
      await api.allocateProfit({ contactId: partner.contactId, date, amountCents: cents, periodFrom: period.from, periodTo: period.to });
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={`${partner.name} · kâr payını sermayeye ekle`} open onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="allocation-form" disabled={saving}>{saving ? "Kaydediliyor…" : "Sermayeye ekle"}</Button>
      </>}>
      <form id="allocation-form" onSubmit={submit} className="space-y-4">
        <ErrorBox message={error} />

        <Field label="Sermayeye eklenecek tutar">
          <NumberInput className={`${inputClass} font-cond text-[18px]`} value={amount} onValueChange={setAmount}
            placeholder="250.000" autoFocus />
        </Field>
        <Field label="Tarih"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} required /></Field>
      </form>
    </Modal>
  );
}
