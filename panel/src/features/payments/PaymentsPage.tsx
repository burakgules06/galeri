// Odemeler: satis taksitleri. Sekmeler: Geciken, Bu hafta (bugun + 6 gun), Tumu. Sadece ADMIN.
// Satirda kisi, plaka, vade, tutar ve yontem; "Tahsil et" hesap ve tarih sorar. Geciken satirlar kirmizi.
import { useMemo, useState } from "react";
import { api } from "../../api/galeri";
import { addDaysIso, tl, todayIso } from "../../shared/format";
import type { InstallmentResponse } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { Empty, ErrorBox, inputClass } from "../../shared/ui/Common";
import { StatTile } from "../../shared/ui/StatTile";
import { InstallmentRow } from "./InstallmentRow";

type Tab = "overdue" | "week" | "all";

const TABS: { value: Tab; label: string }[] = [
  { value: "overdue", label: "Geciken" },
  { value: "week", label: "Bu hafta" },
  { value: "all", label: "Tümü" },
];

export function PaymentsPage() {
  const { data, error, reload } = useLoad(() => api.installments({ direction: "SALE" }), []);
  const [tab, setTab] = useState<Tab>("overdue");
  const [q, setQ] = useState("");
  const today = todayIso();
  const weekEnd = addDaysIso(today, 6);

  const all = useMemo(() => data ?? [], [data]);
  const overdue = all.filter((i) => i.status === "OVERDUE");
  const week = all.filter((i) => i.status !== "PAID" && i.dueDate >= today && i.dueDate <= weekEnd);
  const open = all.filter((i) => i.status !== "PAID");
  const sum = (list: InstallmentResponse[]) => list.reduce((t, i) => t + i.amountCents, 0);

  const base = tab === "overdue" ? [...overdue].sort((a, b) => b.daysLate - a.daysLate)
    : tab === "week" ? week
    : [...open, ...all.filter((i) => i.status === "PAID").reverse()];
  const needle = q.trim().toLocaleLowerCase("tr-TR");
  const digits = q.replace(/\D/g, "").replace(/^0+/, "");
  const rows = needle ? base.filter((i) =>
    i.payer.name.toLocaleLowerCase("tr-TR").includes(needle)
    || (i.vehicle?.plate ?? "").toLocaleLowerCase("tr-TR").replace(/\s/g, "").includes(needle.replace(/\s/g, ""))
    || (digits.length >= 3 && (i.payer.phone ?? "").includes(digits))) : base;

  const counts: Record<Tab, number> = { overdue: overdue.length, week: week.length, all: all.length };

  return (
    <div className="space-y-5">
      <header>
        <p className="etiket">Tahsilat</p>
        <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Ödemeler</h1>
      </header>

      <ErrorBox message={error} />

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <StatTile label="Geciken" value={tl(sum(overdue))} tone="negative" sub={`${overdue.length} taksit`} />
        <StatTile label="Bu hafta" value={tl(sum(week))} tone="brand" sub={`${week.length} taksit, 7 gün içinde`} />
        <StatTile label="Bekleyen toplam" value={tl(sum(open))} sub={`${open.length} taksit`} />
        <StatTile label="Tahsil edilen" value={tl(sum(all.filter((i) => i.status === "PAID")))} tone="positive"
          sub={`${all.filter((i) => i.status === "PAID").length} taksit`} />
      </div>

      <section className="kart overflow-hidden">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 p-3 md:p-4 border-b border-cizgi">
          <div className="flex gap-1 rounded-[12px] bg-yuzey-2 p-1 self-start" role="tablist">
            {TABS.map((t) => (
              <button key={t.value} type="button" role="tab" aria-selected={tab === t.value} onClick={() => setTab(t.value)}
                className={`basilir h-9 px-3 md:px-3.5 rounded-[9px] text-[14px] font-semibold flex items-center gap-2 whitespace-nowrap ${
                  tab === t.value ? "bg-yuzey text-murekkep shadow-sm" : "text-murekkep-2 hover:text-murekkep"}`}>
                {t.label}
                <span className={`sayi text-[13px] rounded-full px-1.5 min-w-[22px] ${
                  t.value === "overdue" && counts.overdue ? "bg-tehlike text-white" : "bg-cizgi text-murekkep-2"}`}>{counts[t.value]}</span>
              </button>
            ))}
          </div>
          <input className={`${inputClass} md:max-w-[300px]`} value={q} onChange={(e) => setQ(e.target.value)}
            placeholder="Kişi, plaka ya da telefon" aria-label="Ödemelerde ara" />
        </div>

        {data && rows.length === 0 && (
          <Empty title={tab === "overdue" ? "Geciken ödeme yok" : tab === "week" ? "Bu hafta vadesi gelen yok" : "Kayıt yok"}
            description={tab === "overdue" && !q ? "Bütün tahsilatlar zamanında." : undefined} />
        )}
        {rows.length > 0 && (
          <ul className="divide-y divide-cizgi px-2 md:px-3">
            {rows.map((i) => <InstallmentRow key={i.id} i={i} onChange={reload} />)}
          </ul>
        )}
      </section>
    </div>
  );
}
