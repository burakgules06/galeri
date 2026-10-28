// Odemeler. Sekmeler: Geciken, Bu hafta (bugun + 6 gun), Tumu (satis taksitleri, "Tahsil et") ve
// Odenecekler (senetli / taksitli alis taksitleri, "Ode") ve Cek / Senet (alinan ve verilen evrak; ciro, karsiliksiz). Sadece ADMIN.
// Satirda kisi, plaka, vade, tutar ve yontem; tahsil / ode hesap ve tarih sorar. Geciken satirlar kirmizi.
import { useMemo, useState } from "react";
import { api } from "../../api/galeri";
import { addDaysIso, tl, todayIso } from "../../shared/format";
import type { InstallmentResponse } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox, inputClass, Segment } from "../../shared/ui/Common";
import { OpenItemsForm } from "../contacts/OpenItemsForm";
import { StatTile } from "../../shared/ui/StatTile";
import { InstallmentRow } from "./InstallmentRow";

type Tab = "overdue" | "week" | "all" | "payables" | "instruments";
type Side = "SALE" | "PURCHASE";

const TABS: { value: Tab; label: string }[] = [
  { value: "all", label: "Tümü" },
  { value: "overdue", label: "Geciken" },
  { value: "week", label: "Bu hafta" },
  { value: "payables", label: "Ödenecekler" },
  { value: "instruments", label: "Çek / Senet" },
];

export function PaymentsPage() {
  const { data, error, reload } = useLoad(() => api.installments(), []);
  const [tab, setTab] = useState<Tab>("all");
  const [q, setQ] = useState("");
  const [side, setSide] = useState<Side>("SALE");
  const [importing, setImporting] = useState(false);
  const today = todayIso();
  const weekEnd = addDaysIso(today, 6);

  const all = useMemo(() => (data ?? []).filter((i) => i.direction === "SALE"), [data]);
  const purchases = useMemo(() => (data ?? []).filter((i) => i.direction === "PURCHASE"), [data]);
  const overdue = all.filter((i) => i.status === "OVERDUE");
  const week = all.filter((i) => i.status !== "PAID" && i.dueDate >= today && i.dueDate <= weekEnd);
  const open = all.filter((i) => i.status !== "PAID");
  const payable = purchases.filter((i) => i.status !== "PAID");
  const instruments = useMemo(() => (data ?? []).filter((i) => i.method === "CHEQUE" || i.method === "PROMISSORY_NOTE"), [data]);
  const sum = (list: InstallmentResponse[]) => list.reduce((t, i) => t + i.amountCents, 0);
  const openFirst = (list: InstallmentResponse[]) =>
    [...list.filter((i) => i.status !== "PAID"), ...list.filter((i) => i.status === "PAID").reverse()];

  const base = tab === "overdue" ? [...overdue].sort((a, b) => b.daysLate - a.daysLate)
    : tab === "week" ? week
    : tab === "payables" ? openFirst(purchases)
    : tab === "instruments" ? openFirst(instruments.filter((i) => i.direction === side))
    : openFirst(all);
  const needle = q.trim().toLocaleLowerCase("tr-TR");
  const digits = q.replace(/\D/g, "").replace(/^0+/, "");
  const rows = needle ? base.filter((i) =>
    i.payer.name.toLocaleLowerCase("tr-TR").includes(needle)
    || (i.vehicle?.plate ?? "").toLocaleLowerCase("tr-TR").replace(/\s/g, "").includes(needle.replace(/\s/g, ""))
    || (i.noteNo ?? "").toLocaleLowerCase("tr-TR").includes(needle)
    || (digits.length >= 3 && (i.payer.phone ?? "").includes(digits))) : base;

  const counts: Record<Tab, number> = {
    overdue: overdue.length, week: week.length, all: all.length, payables: payable.length,
    instruments: instruments.filter((i) => i.status !== "PAID").length,
  };
  const paidSales = all.filter((i) => i.status === "PAID");

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="etiket">Tahsilat</p>
          <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Ödemeler</h1>
        </div>
        <Button variant="secondary" onClick={() => setImporting(true)}>Geçmiş borç ekle</Button>
      </header>
      {importing && <OpenItemsForm onClose={() => setImporting(false)} onSaved={() => { setImporting(false); reload(); }} />}

      <ErrorBox message={error} />

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <StatTile label="Geciken" value={tl(sum(overdue))} tone="negative" sub={`${overdue.length} taksit`} />
        <StatTile label="Bu hafta" value={tl(sum(week))} tone="brand" sub={`${week.length} taksit, 7 gün içinde`} />
        <StatTile label="Bekleyen tahsilat" value={tl(sum(open))} tone="positive"
          sub={`${open.length} taksit · tahsil edilen ${tl(sum(paidSales))}`} />
        <StatTile label="Ödenecek" value={tl(sum(payable))} tone={payable.length ? "negative" : "neutral"}
          sub={`${payable.length} taksit · senetli alışlar`} />
      </div>

      <section className="kart overflow-hidden">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 p-3 md:p-4 border-b border-cizgi">
          <div className="flex gap-1 rounded-[12px] bg-yuzey-2 p-1 self-start max-w-full overflow-x-auto" role="tablist">
            {TABS.map((t) => (
              <button key={t.value} type="button" role="tab" aria-selected={tab === t.value} onClick={() => setTab(t.value)}
                className={`basilir shrink-0 h-9 px-3 md:px-3.5 rounded-[9px] text-[14px] font-semibold flex items-center gap-2 whitespace-nowrap ${
                  tab === t.value ? "bg-yuzey text-murekkep shadow-sm" : "text-murekkep-2 hover:text-murekkep"}`}>
                {t.label}
                <span className={`sayi text-[13px] rounded-full px-1.5 min-w-[22px] ${
                  t.value === "overdue" && counts.overdue ? "bg-tehlike text-white" : "bg-cizgi text-murekkep-2"}`}>{counts[t.value]}</span>
              </button>
            ))}
          </div>
          <input className={`${inputClass} md:max-w-[300px]`} value={q} onChange={(e) => setQ(e.target.value)}
            placeholder="Kişi, plaka, telefon ya da evrak no" aria-label="Ödemelerde ara" />
        </div>
        {tab === "instruments" && (
          <div className="px-3 md:px-4 pt-3">
            <Segment<Side> value={side} onChange={setSide}
              options={[{ value: "SALE", label: "Alınan" }, { value: "PURCHASE", label: "Verilen" }]} />
          </div>
        )}

        {data && rows.length === 0 && (
          <Empty title={tab === "overdue" ? "Geciken ödeme yok" : tab === "week" ? "Bu hafta vadesi gelen yok"
            : tab === "payables" ? "Ödenecek senet ya da taksit yok"
            : tab === "instruments" ? "Çek ya da senet yok" : "Kayıt yok"}
            description={tab === "overdue" && !q ? "Bütün tahsilatlar zamanında." : undefined} />
        )}
        {rows.length > 0 && (
          <ul className="divide-y divide-cizgi px-2 md:px-3">
            {rows.map((i) => <InstallmentRow key={i.id} i={i} onChange={reload} allowDeleteOpenItem />)}
          </ul>
        )}
      </section>
    </div>
  );
}
