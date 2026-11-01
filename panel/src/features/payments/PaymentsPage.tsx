// Odemeler. Ustte iki sekme: "Ödemeler" (alacaklar, satis taksitleri, "Tahsil et") ve "Ödeyeceklerim" (alis taksitleri, "Öde").
// Altinda yonteme gore: Tumu, Cek, Senet, Acik hesap, Kredi karti, Geciken. Her plan tek satirdir; acilinca butun taksitleri ve
// plan ozeti gorunur. Kisi secimi: parasi o kisinin olan planlar (aracin sahipleri ya da evrak sahibi). Siralama sagda.
import { useMemo, useState } from "react";
import { api } from "../../api/galeri";
import { addDaysIso, formatDate, METHOD, tl, todayIso } from "../../shared/format";
import type { InstallmentResponse, PaymentMethod, PlanDirection } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox, inputClass } from "../../shared/ui/Common";
import { Plate } from "../../shared/ui/Plate";
import { StatTile } from "../../shared/ui/StatTile";
import { OpenItemsForm } from "../contacts/OpenItemsForm";
import { InstallmentRow } from "./InstallmentRow";

type Sub = "all" | "CHEQUE" | "PROMISSORY_NOTE" | "OPEN_ACCOUNT" | "CREDIT_CARD" | "overdue";
type Sort = "created-desc" | "created-asc" | "due-asc" | "due-desc";

const SUBS: { value: Sub; label: string }[] = [
  { value: "all", label: "Tümü" },
  { value: "CHEQUE", label: "Çek" },
  { value: "PROMISSORY_NOTE", label: "Senet" },
  { value: "OPEN_ACCOUNT", label: "Açık hesap" },
  { value: "CREDIT_CARD", label: "Kredi kartı" },
  { value: "overdue", label: "Geciken" },
];

const SORTS: { value: Sort; label: string }[] = [
  { value: "created-desc", label: "Oluşturma: yeni → eski" },
  { value: "created-asc", label: "Oluşturma: eski → yeni" },
  { value: "due-asc", label: "Vade: yakın → uzak" },
  { value: "due-desc", label: "Vade: uzak → yakın" },
];

/** Bir odeme plani: ayni planin taksitleri. */
interface Plan {
  id: string;
  direction: PlanDirection;
  counterparty: InstallmentResponse["counterparty"];
  vehicle: InstallmentResponse["vehicle"];
  createdAt: string;
  items: InstallmentResponse[];
  total: number;
  paid: number;
  overdue: number;
  userIds: string[];
}

const SORT_KEY = "galeri_payments_sort";

function readSort(): Sort {
  try {
    const v = localStorage.getItem(SORT_KEY);
    return SORTS.some((s) => s.value === v) ? (v as Sort) : "created-desc";
  } catch {
    return "created-desc";
  }
}

export function PaymentsPage() {
  const { data, error, reload } = useLoad(() => api.installments(), []);
  const holders = useLoad(() => api.holders(), []);
  const [side, setSide] = useState<PlanDirection>("SALE");
  const [sub, setSub] = useState<Sub>("all");
  const [person, setPerson] = useState("");
  const [sort, setSortState] = useState<Sort>(readSort);
  const [q, setQ] = useState("");
  const [open, setOpen] = useState<Set<string>>(new Set());
  const [importing, setImporting] = useState(false);
  const today = todayIso();
  const weekEnd = addDaysIso(today, 6);

  const setSort = (s: Sort) => {
    setSortState(s);
    try { localStorage.setItem(SORT_KEY, s); } catch { /* tarayici izin vermiyor */ }
  };

  const plans = useMemo(() => {
    const map = new Map<string, Plan>();
    for (const i of data ?? []) {
      let p = map.get(i.planId);
      if (!p) {
        p = { id: i.planId, direction: i.direction, counterparty: i.counterparty, vehicle: i.vehicle, createdAt: i.planCreatedAt ?? "",
          items: [], total: 0, paid: 0, overdue: 0, userIds: i.userIds ?? [] };
        map.set(i.planId, p);
      }
      p.items.push(i);
      p.total += i.amountCents;
      if (i.status === "PAID") p.paid += i.amountCents;
      if (i.status === "OVERDUE") p.overdue += i.amountCents;
    }
    for (const p of map.values()) p.items.sort((a, b) => a.seq - b.seq);
    return [...map.values()];
  }, [data]);

  const mine = plans.filter((p) => p.direction === side && (!person || p.userIds.includes(person)));
  const matches = (p: Plan, s: Sub) => s === "all" ? true
    : s === "overdue" ? p.overdue > 0
    : p.items.some((i) => i.method === (s as PaymentMethod));
  const needle = q.trim().toLocaleLowerCase("tr-TR");
  const searched = needle ? mine.filter((p) => p.items.some((i) =>
    i.payer.name.toLocaleLowerCase("tr-TR").includes(needle)
    || p.counterparty.name.toLocaleLowerCase("tr-TR").includes(needle)
    || (p.vehicle?.plate ?? "").toLocaleLowerCase("tr-TR").replace(/\s/g, "").includes(needle.replace(/\s/g, ""))
    || (i.noteNo ?? "").toLocaleLowerCase("tr-TR").includes(needle))) : mine;

  // Siralamada vade: planin (sekmeye uyan) ilk acik taksitinin vadesi; hepsi odendiyse son vade
  const dueOf = (p: Plan) => {
    const relevant = p.items.filter((i) => sub === "all" || sub === "overdue" || i.method === sub);
    const openItems = relevant.filter((i) => i.status !== "PAID");
    return (openItems[0] ?? relevant.at(-1) ?? p.items[0]).dueDate;
  };
  const list = searched.filter((p) => matches(p, sub)).sort((a, b) => {
    // Acik planlar once, tamamen odenenler sonda
    const doneA = a.paid === a.total ? 1 : 0, doneB = b.paid === b.total ? 1 : 0;
    if (doneA !== doneB) return doneA - doneB;
    if (sort === "created-desc") return b.createdAt.localeCompare(a.createdAt);
    if (sort === "created-asc") return a.createdAt.localeCompare(b.createdAt);
    if (sort === "due-asc") return dueOf(a).localeCompare(dueOf(b));
    return dueOf(b).localeCompare(dueOf(a));
  });

  const items = mine.flatMap((p) => p.items);
  const sum = (l: InstallmentResponse[]) => l.reduce((t, i) => t + i.amountCents, 0);
  const overdue = items.filter((i) => i.status === "OVERDUE");
  const week = items.filter((i) => i.status !== "PAID" && i.dueDate >= today && i.dueDate <= weekEnd);
  const waiting = items.filter((i) => i.status !== "PAID");
  const done = items.filter((i) => i.status === "PAID");
  const counts = Object.fromEntries(SUBS.map((s) => [s.value, mine.filter((p) => p.paid < p.total && matches(p, s.value)).length])) as Record<Sub, number>;
  const people = (holders.data ?? []).filter((h) => h.userId);
  const toggle = (id: string) => setOpen((s) => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  const sale = side === "SALE";

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="etiket">Tahsilat ve ödeme</p>
          <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Ödemeler</h1>
        </div>
        <div className="flex items-center gap-2 w-full sm:w-auto">
          <select className={`${inputClass} grow sm:w-[230px]`} value={person} onChange={(e) => setPerson(e.target.value)} aria-label="Kişi">
            <option value="">Herkes</option>
            {people.map((h) => <option key={h.userId!} value={h.userId!}>{h.name}{h.owner ? " (dükkân sahibi)" : ""}</option>)}
          </select>
          <Button variant="secondary" className="shrink-0" onClick={() => setImporting(true)}>Geçmiş borç ekle</Button>
        </div>
      </header>
      {importing && <OpenItemsForm onClose={() => setImporting(false)} onSaved={() => { setImporting(false); reload(); }} />}

      <ErrorBox message={error} />

      <div className="flex gap-1 rounded-[14px] bg-yuzey-2 p-1 w-full sm:w-auto sm:inline-flex" role="tablist" aria-label="Yön">
        {([["SALE", "Ödemeler", "alacaklarım"], ["PURCHASE", "Ödeyeceklerim", "borçlarım"]] as const).map(([v, label, hint]) => (
          <button key={v} type="button" role="tab" aria-selected={side === v} onClick={() => { setSide(v); setOpen(new Set()); }}
            className={`basilir grow sm:grow-0 h-11 px-5 rounded-[11px] text-[15px] font-semibold ${
              side === v ? "bg-yuzey text-murekkep shadow-sm" : "text-murekkep-2 hover:text-murekkep"}`}>
            {label} <span className="hidden sm:inline text-[12px] font-medium text-murekkep-3">· {hint}</span>
          </button>
        ))}
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <StatTile label="Geciken" value={tl(sum(overdue))} tone="negative" sub={`${overdue.length} taksit`} />
        <StatTile label="Bu hafta" value={tl(sum(week))} tone="brand" sub={`${week.length} taksit, 7 gün içinde`} />
        <StatTile label={sale ? "Bekleyen tahsilat" : "Ödenecek"} value={tl(sum(waiting))} tone={sale ? "positive" : "negative"}
          sub={`${waiting.length} taksit`} />
        <StatTile label={sale ? "Tahsil edilen" : "Ödenen"} value={tl(sum(done))} tone="neutral" sub={`${done.length} taksit`} />
      </div>

      <section className="kart overflow-hidden">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3 p-3 md:p-4 border-b border-cizgi">
          <div className="flex gap-1 rounded-[12px] bg-yuzey-2 p-1 self-start max-w-full overflow-x-auto" role="tablist" aria-label="Tür">
            {SUBS.map((t) => (
              <button key={t.value} type="button" role="tab" aria-selected={sub === t.value} onClick={() => setSub(t.value)}
                className={`basilir shrink-0 h-9 px-3 rounded-[9px] text-[14px] font-semibold flex items-center gap-2 whitespace-nowrap ${
                  sub === t.value ? "bg-yuzey text-murekkep shadow-sm" : "text-murekkep-2 hover:text-murekkep"}`}>
                {t.label}
                <span className={`sayi text-[13px] rounded-full px-1.5 min-w-[22px] ${
                  t.value === "overdue" && counts.overdue ? "bg-tehlike text-white" : "bg-cizgi text-murekkep-2"}`}>{counts[t.value]}</span>
              </button>
            ))}
          </div>
          <div className="flex flex-col sm:flex-row gap-2 lg:max-w-[520px] w-full lg:w-auto">
            <input className={`${inputClass} sm:w-[240px]`} value={q} onChange={(e) => setQ(e.target.value)}
              placeholder="Kişi, plaka ya da evrak no" aria-label="Ödemelerde ara" />
            <select className={`${inputClass} sm:w-[220px]`} value={sort} onChange={(e) => setSort(e.target.value as Sort)} aria-label="Sıralama">
              {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
            </select>
          </div>
        </div>

        {data && list.length === 0 && (
          <Empty title={sub === "overdue" ? "Geciken ödeme yok" : sale ? "Alacak kaydı yok" : "Ödenecek kayıt yok"}
            description={person ? "Seçili kişi için kayıt yok." : undefined} />
        )}
        {list.length > 0 && (
          <ul className="divide-y divide-cizgi">
            {list.map((p) => <PlanItem key={p.id} plan={p} open={open.has(p.id)} onToggle={() => toggle(p.id)} onChange={reload} sub={sub} />)}
          </ul>
        )}
      </section>
    </div>
  );
}

function PlanItem({ plan, open, onToggle, onChange, sub }: { plan: Plan; open: boolean; onToggle: () => void; onChange: () => void; sub: Sub }) {
  const openItems = plan.items.filter((i) => i.status !== "PAID");
  const next = openItems.find((i) => sub === "all" || sub === "overdue" || i.method === sub) ?? openItems[0];
  const methods = [...new Set(plan.items.filter((i) => !i.downPayment).map((i) => i.method))];
  const pct = plan.total > 0 ? Math.round((plan.paid * 100) / plan.total) : 0;
  const remaining = plan.total - plan.paid;
  const done = remaining === 0;
  const holder = plan.items.find((i) => i.holder)?.holder;

  return (
    <li>
      <button type="button" onClick={onToggle} aria-expanded={open}
        className="w-full text-left grid grid-cols-[1fr_auto] md:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_200px_28px] gap-x-4 gap-y-2 items-center px-3 md:px-4 py-3.5 hover:bg-yuzey-2/60">
        <div className="min-w-0">
          <div className="flex items-center gap-2 min-w-0">
            <span className="font-semibold text-[15.5px] truncate">{plan.counterparty.name}</span>
            {plan.overdue > 0 && <span className="shrink-0 text-[12px] font-semibold rounded-full px-2 py-0.5 bg-tehlike-zemin text-tehlike">Gecikti</span>}
            {done && <span className="shrink-0 text-[12px] font-semibold rounded-full px-2 py-0.5 bg-basari-zemin text-basari">Kapandı</span>}
          </div>
          <div className="mt-1 flex items-center gap-2 flex-wrap text-[13px] text-murekkep-2">
            {plan.vehicle && <><Plate plate={plan.vehicle.plate} size="sm" /><span className="hidden md:inline truncate">{plan.vehicle.make} {plan.vehicle.model}</span></>}
            {!plan.vehicle && <span>Geçmiş kayıt</span>}
            {methods.map((m) => <span key={m} className="rounded bg-yuzey-2 border border-cizgi px-1.5 py-0.5 text-[12px] font-semibold">{METHOD[m]}</span>)}
            {holder && <span className="text-[12px] font-semibold text-plaka-koyu bg-plaka-acik rounded px-1.5 py-0.5">{holder.name}</span>}
          </div>
        </div>

        <div className="hidden md:block min-w-0">
          <div className="flex justify-between text-[12.5px] text-murekkep-2">
            <span>{plan.items.filter((i) => i.status === "PAID").length} / {plan.items.length} ödendi</span>
            <span className="sayi">{pct}%</span>
          </div>
          <div className="mt-1 h-2 rounded-full bg-yuzey-2 overflow-hidden">
            <div className={`h-full rounded-full ${plan.overdue > 0 ? "bg-tehlike-canli" : "bg-basari-canli"}`} style={{ width: `${pct}%` }} />
          </div>
          <p className="mt-1 text-[12.5px] text-murekkep-3">Toplam <span className="sayi">{tl(plan.total)}</span></p>
        </div>

        <div className="text-right min-w-0">
          {next ? <>
            <p className={`sayi font-bold text-[19px] whitespace-nowrap ${next.status === "OVERDUE" ? "text-tehlike" : ""}`}>{tl(next.amountCents)}</p>
            <p className="text-[12.5px] text-murekkep-2 whitespace-nowrap">
              {next.status === "OVERDUE" ? `${next.daysLate} gün gecikti` : `Vade ${formatDate(next.dueDate)}`} · {METHOD[next.method]}
            </p>
          </> : <>
            <p className="sayi font-bold text-[19px] text-basari whitespace-nowrap">{tl(plan.total)}</p>
            <p className="text-[12.5px] text-murekkep-2">Hepsi ödendi</p>
          </>}
          <p className="md:hidden text-[12px] text-murekkep-3 mt-0.5">Kalan <span className="sayi">{tl(remaining)}</span></p>
        </div>

        <svg className={`hidden md:block justify-self-end text-murekkep-3 transition-transform ${open ? "rotate-180" : ""}`} width="18" height="18"
          viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M6 9l6 6 6-6" /></svg>
      </button>

      {open && (
        <div className="bg-yuzey-2/50 border-t border-cizgi px-2 md:px-4 pb-3">
          <dl className="flex flex-wrap gap-x-6 gap-y-1 px-2 pt-3 pb-1 text-[13.5px]">
            <PlanFact label="Toplam" cents={plan.total} />
            <PlanFact label={plan.direction === "SALE" ? "Tahsil edilen" : "Ödenen"} cents={plan.paid} tone="text-basari" />
            <PlanFact label="Kalan" cents={remaining} tone={plan.overdue > 0 ? "text-tehlike" : ""} />
            <div className="flex gap-1.5"><dt className="text-murekkep-2">Taksit</dt><dd className="font-semibold">{plan.items.length}</dd></div>
          </dl>
          <ul className="divide-y divide-cizgi">
            {plan.items.map((i) => (
              <InstallmentRow key={i.id} i={i} onChange={onChange} showVehicle={false} showPayer={i.payer.id !== plan.counterparty.id} allowDeleteOpenItem />
            ))}
          </ul>
        </div>
      )}
    </li>
  );
}

function PlanFact({ label, cents, tone = "" }: { label: string; cents: number; tone?: string }) {
  return (
    <div className="flex gap-1.5 items-baseline">
      <dt className="text-murekkep-2">{label}</dt>
      <dd className={`sayi font-semibold text-[15px] ${tone}`}>{tl(cents)}</dd>
    </div>
  );
}
