// Kasa: nakit (kasa + bankalar; Tumu ya da secilen kisinin her hesaptaki parasi) ve dort sekme: hareketler (ortak payi, alacak / verecek, hareket listesi),
// ortaklar (sermaye girisi / cekisi, dukkan sahibi), donem kari (kar payini sermayeye ekleme). Sadece ADMIN.
// Bakiyeler ve paylar hareketlerden hesaplanir; ortak ve cari ozeti /api/dashboard'dan gelir.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { SplitBar } from "../../shared/charts/Donut";
import { entryDirection, percent, SERIES, tl } from "../../shared/format";
import type { LedgerEntryResponse } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox, inputClass, Segment } from "../../shared/ui/Common";
import { BalancesCard } from "./BalancesCard";
import { PartnerCapitalCard } from "../home/PartnerCapitalCard";
import { BulkExpenseForm } from "./BulkExpenseForm";
import { LedgerEntryList } from "./LedgerEntryList";
import { PartnersPanel } from "./PartnersPanel";
import { PeriodProfitPanel } from "./PeriodProfitPanel";
import { NetWorthPanel } from "./NetWorthPanel";
import { TransferForm } from "./TreasuryForms";

type Tab = "entries" | "partners" | "profit" | "assets";

type Sort = "new" | "old";
type Flow = "" | "in" | "out";

export function LedgerPage() {
  const [selected, setSelected] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("entries");
  const [revision, setRevision] = useState(0);
  const [expenseOpen, setExpenseOpen] = useState(false);
  const [bankOpen, setBankOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);
  const [owner, setOwner] = useState("");           // kimin parasi: "" = Tumu
  const [listTab, setListTab] = useState<"entries" | "corrections">("entries");
  const [actor, setActor] = useState("");
  const [flow, setFlow] = useState<Flow>("");
  const [sort, setSort] = useState<Sort>("new");
  const [entryPage, setEntryPage] = useState(1);
  const accounts = useLoad(() => api.accounts(), []);
  const holdings = useLoad(() => api.holdings(), [revision]);
  const summary = useLoad(() => api.dashboard(), []);
  const entries = useLoad(() => api.ledgerEntries(selected), [selected]);

  const reload = () => { accounts.reload(); entries.reload(); summary.reload(); setRevision(r => r + 1); };
  const list = accounts.data ?? [];
  const total = list.reduce((t, a) => t + a.balanceCents, 0);
  const colored = list.map((a, i) => ({ ...a, color: SERIES[i % SERIES.length] }));
  // Kisiler: hesaplarda parasi olanlar (dukkan sahibi once)
  const people = new Map<string, { key: string; name: string; owner: boolean }>();
  for (const h of holdings.data ?? []) for (const p of h.parts) {
    const key = p.userId ?? "owner";
    if (!people.has(key)) people.set(key, { key, name: p.name, owner: p.owner });
  }
  const peopleList = [...people.values()].sort((x, y) => Number(y.owner) - Number(x.owner) || x.name.localeCompare(y.name, "tr"));
  const amountOf = (accountId: string) => {
    const h = (holdings.data ?? []).find((x) => x.accountId === accountId);
    if (!owner) return h?.balanceCents ?? list.find((a) => a.id === accountId)?.balanceCents ?? 0;
    return (h?.parts ?? []).filter((p) => (p.userId ?? "owner") === owner).reduce((t, p) => t + p.cents, 0);
  };
  const shown = colored.map((a) => ({ ...a, amount: amountOf(a.id) }));
  const shownTotal = owner ? shown.reduce((t, a) => t + a.amount, 0) : total;

  const allEntries = entries.data ?? [];
  const isCorrection = (e: LedgerEntryResponse) => e.corrected || e.correctedEntryId != null;
  const actors = [...new Set(allEntries.map((e) => e.createdBy).filter((n): n is string => !!n))].sort((x, y) => x.localeCompare(y, "tr"));
  const filtered = allEntries
    .filter((e) => (listTab === "corrections") === isCorrection(e))
    .filter((e) => (!actor || e.createdBy === actor)
      && (!flow || (flow === "in" ? entryDirection(e, selected) > 0 : entryDirection(e, selected) < 0)))
    .sort((x, y) => {
      const c = x.date.localeCompare(y.date) || (x.createdAt ?? "").localeCompare(y.createdAt ?? "");
      return sort === "new" ? -c : c;
    });
  const correctionCount = allEntries.filter(isCorrection).length;
  const hasFilters = !!(actor || flow);
  const pageSize = 20;
  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
  const page = Math.min(entryPage, pageCount);
  const pageEntries = filtered.slice((page - 1) * pageSize, page * pageSize);
  const changeAccount = (id: string | null) => { setSelected(id); setEntryPage(1); };

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="etiket">Para defteri</p>
          <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Kasa ve banka</h1>
        </div>
        <div className="flex flex-wrap gap-2">
          <a href="#/expenses" className="md:hidden basilir h-10 px-3 rounded-lg border border-cizgi-2 bg-yuzey font-semibold text-[14px] grid place-items-center">Masraflar</a>
          <a href="#/shop" className="md:hidden basilir h-10 px-3 rounded-lg border border-cizgi-2 bg-yuzey font-semibold text-[14px] grid place-items-center">Dükkân</a>
          <Button variant="secondary" onClick={() => setTransferOpen(true)}>Hesaplar arası transfer</Button>
          <Button onClick={() => setExpenseOpen(true)}>Masraf ekle</Button>
        </div>
      </header>

      <ErrorBox message={accounts.error ?? summary.error ?? holdings.error} />

      {/* Kimin parasi: Tumu ya da bir kisi; hesap kartlari secili kisinin o hesaptaki parasini gosterir. Karta dokununca hareketler suzulur. */}
      <section className="kart p-4 md:p-5">
        <div className="flex flex-wrap items-center gap-1.5" role="radiogroup" aria-label="Kimin parası">
          <PersonChip label="Tümü" on={!owner} onClick={() => setOwner("")} />
          {peopleList.map((p) => <PersonChip key={p.key} label={p.name} sub={p.owner ? "dükkân sahibi" : undefined} on={owner === p.key} onClick={() => setOwner(p.key)} />)}
        </div>
        <div className="mt-4 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="etiket">{owner ? `${people.get(owner)?.name ?? ""} · kasa ve bankalarda` : "Toplam nakit"}</p>
            <p className={`sayi font-bold text-[34px] md:text-[40px] leading-none tracking-tight mt-1.5 ${shownTotal < 0 ? "text-tehlike" : ""}`}>
              {accounts.data ? tl(shownTotal) : "…"}
            </p>
          </div>
          {bankOpen
            ? <BankForm onClose={() => setBankOpen(false)} onAdded={() => { setBankOpen(false); reload(); }} />
            : <Button variant="secondary" onClick={() => setBankOpen(true)}>+ Banka hesabı</Button>}
        </div>
        <div className="mt-4"><SplitBar height={10} parts={shown.map((a) => ({ key: a.id, value: Math.max(0, a.amount), color: a.color }))} /></div>
        <div className="mt-3 grid grid-cols-2 md:grid-cols-4 gap-2">
          <AccountButton name="Tüm hesaplar" balance={shownTotal} share={null} color={null} selected={selected === null} onClick={() => changeAccount(null)} />
          {shown.map((a) => (
            <AccountButton key={a.id} name={a.name} balance={a.amount} color={a.color}
              share={shownTotal > 0 && a.amount > 0 ? Math.round((a.amount * 10000) / shownTotal) : null}
              selected={selected === a.id} onClick={() => changeAccount(a.id)}
              parts={owner ? undefined : (holdings.data ?? []).find((h) => h.accountId === a.id)?.parts.filter((p) => p.cents !== 0)} />
          ))}
        </div>
      </section>

      <Segment<Tab> value={tab} onChange={setTab} options={[
        { value: "entries", label: "Hareketler" }, { value: "partners", label: "Ortaklar ve sermaye" }, { value: "profit", label: "Dönem kârı" },
        { value: "assets", label: "Varlıklar" },
      ]} />

      {tab === "partners" && <PartnersPanel onChange={reload} />}
      {tab === "profit" && <PeriodProfitPanel />}
      {tab === "assets" && <NetWorthPanel revision={revision} />}

      {tab === "entries" && summary.data && (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
          <PartnerCapitalCard revision={revision} stackStatement />
          <BalancesCard receivableCents={summary.data.receivableCents} payableCents={summary.data.payableCents}
            receivables={summary.data.receivables} payables={summary.data.payables} />
        </div>
      )}

      {tab === "entries" && <section className="kart p-4 md:p-5">
        <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
          <div className="flex gap-1 rounded-[12px] bg-yuzey-2 p-1" role="tablist" aria-label="Hareket listesi">
            {([["entries", "Son hareketler"], ["corrections", "Düzeltmeler"]] as const).map(([v, label]) => (
              <button key={v} type="button" role="tab" aria-selected={listTab === v} onClick={() => { setListTab(v); setEntryPage(1); }}
                className={`basilir h-9 px-3.5 rounded-[9px] text-[14px] font-semibold flex items-center gap-2 ${
                  listTab === v ? "bg-yuzey text-murekkep shadow-sm" : "text-murekkep-2 hover:text-murekkep"}`}>
                {label}
                {v === "corrections" && <span className="sayi text-[13px] rounded-full px-1.5 min-w-[22px] bg-cizgi text-murekkep-2">{correctionCount}</span>}
              </button>
            ))}
          </div>
          {entries.data && <span className="text-[13px] text-murekkep-2">{filtered.length} kayıt</span>}
        </div>
        <ErrorBox message={entries.error} />
        <div className="mb-4 grid gap-2 grid-cols-2 lg:grid-cols-[minmax(160px,1fr)_minmax(160px,1fr)_160px_200px_auto] lg:items-end">
          <label className="text-[12px] text-murekkep-2">İşlem yapılan yer
            <select className={`${inputClass} mt-1 h-9 w-full`} value={selected ?? ""} onChange={(e) => changeAccount(e.target.value || null)}>
              <option value="">Tüm hesaplar</option>{list.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          </label>
          <label className="text-[12px] text-murekkep-2">İşlemi yapan
            <select className={`${inputClass} mt-1 h-9 w-full`} value={actor} onChange={(e) => { setActor(e.target.value); setEntryPage(1); }}>
              <option value="">Herkes</option>{actors.map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </label>
          <label className="text-[12px] text-murekkep-2">İşlem tipi
            <select className={`${inputClass} mt-1 h-9 w-full`} value={flow} onChange={(e) => { setFlow(e.target.value as Flow); setEntryPage(1); }}>
              <option value="">Giriş ve çıkış</option><option value="in">Giriş</option><option value="out">Çıkış</option>
            </select>
          </label>
          <label className="text-[12px] text-murekkep-2">Sıralama
            <select className={`${inputClass} mt-1 h-9 w-full`} value={sort} onChange={(e) => setSort(e.target.value as Sort)}>
              <option value="new">Tarih: yeni → eski</option><option value="old">Tarih: eski → yeni</option>
            </select>
          </label>
          <div className="col-span-2 lg:col-span-1 min-h-9 flex items-end">
            {hasFilters && <Button type="button" variant="quiet" className="h-9" onClick={() => { setActor(""); setFlow(""); setEntryPage(1); }}>Temizle</Button>}
          </div>
        </div>
        {entries.data && filtered.length === 0 && (
          <Empty title={listTab === "corrections" ? "Düzeltme yok" : allEntries.length === 0 ? "Henüz hareket yok" : "Filtreye uygun hareket yok"}
            description={listTab === "entries" && allEntries.length === 0 ? "Masraf girdiğinde ya da kasadan araç aldığında burada görünür." : undefined} />
        )}
        {entries.data && filtered.length > 0 && (
          <LedgerEntryList entries={pageEntries} direction={(e) => entryDirection(e, selected)} onChange={reload}
            corrections={listTab === "corrections" ? "only" : "hide"} />
        )}
        {entries.data && filtered.length > pageSize && <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-cizgi pt-3">
          <span className="text-[13px] text-murekkep-2">{(page - 1) * pageSize + 1}–{Math.min(page * pageSize, filtered.length)} / {filtered.length} hareket</span>
          <div className="flex items-center gap-2">
            <Button type="button" variant="secondary" className="h-9" disabled={page === 1} onClick={() => setEntryPage(Math.max(1, page - 1))}>Önceki</Button>
            <span className="min-w-16 text-center text-[13px] font-semibold">{page} / {pageCount}</span>
            <Button type="button" variant="secondary" className="h-9" disabled={page === pageCount} onClick={() => setEntryPage(Math.min(pageCount, page + 1))}>Sonraki</Button>
          </div>
        </div>}
      </section>}

      {transferOpen && <TransferForm onClose={() => setTransferOpen(false)} onSaved={() => { setTransferOpen(false); reload(); }} />}
      {expenseOpen && <BulkExpenseForm onClose={() => setExpenseOpen(false)} onSaved={() => { setExpenseOpen(false); reload(); }} />}
    </div>
  );
}

function PersonChip({ label, sub, on, onClick }: { label: string; sub?: string; on: boolean; onClick: () => void }) {
  return (
    <button type="button" role="radio" aria-checked={on} onClick={onClick}
      className={`basilir h-9 px-3 rounded-full border text-[14px] font-semibold whitespace-nowrap ${
        on ? "bg-murekkep text-white border-murekkep" : "bg-yuzey text-murekkep-2 border-cizgi-2 hover:bg-yuzey-2"}`}>
      {label}{sub && <span className={`ml-1 text-[12px] font-medium ${on ? "text-white/70" : "text-murekkep-3"}`}>· {sub}</span>}
    </button>
  );
}

function AccountButton({ name, balance, share, color, selected, onClick, parts }: {
  name: string; balance: number; share: number | null; color: string | null; selected: boolean; onClick: () => void;
  parts?: { userId: string | null; name: string; cents: number }[];   // Tumu seciliyken hesaptaki paranin kisilere dagilimi
}) {
  return (
    <button type="button" onClick={onClick} aria-pressed={selected}
      className={`basilir text-left rounded-[12px] border px-3.5 py-2.5 ${selected ? "bg-plaka-acik border-plaka" : "bg-yuzey border-cizgi hover:bg-yuzey-2"}`}>
      <span className="flex items-center gap-1.5 text-[13px] font-semibold truncate text-murekkep-2">
        {color && <span className="w-2.5 h-2.5 rounded-[3px] shrink-0" style={{ background: color }} />}
        <span className="truncate">{name}</span>
        {share != null && <span className="ml-auto text-murekkep-3 font-medium">{percent(share, 0)}</span>}
      </span>
      <span className={`block sayi font-bold text-[20px] leading-tight mt-0.5 ${balance < 0 ? "text-tehlike" : selected ? "text-plaka-koyu" : "text-murekkep"}`}>{tl(balance)}</span>
      {parts && parts.length > 0 && (
        <span className="mt-1.5 block space-y-0.5">
          {parts.map((p) => (
            <span key={p.userId ?? "owner"} className="flex items-baseline justify-between gap-2 text-[12px] text-murekkep-2">
              <span className="truncate">{p.name}</span>
              <span className={`sayi whitespace-nowrap ${p.cents < 0 ? "text-tehlike" : ""}`}>{tl(p.cents)}</span>
            </span>
          ))}
        </span>
      )}
    </button>
  );
}

function BankForm({ onClose, onAdded }: { onClose: () => void; onAdded: () => void }) {
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    try {
      await api.addBank(name.trim());
      onAdded();
    } catch (err) {
      setError(errorMessage(err));
    }
  };
  return (
    <form onSubmit={submit} className="flex flex-wrap items-center gap-1.5">
      <input className={`${inputClass} h-10 w-48`} value={name} onChange={(e) => setName(e.target.value)} placeholder="Banka adı" autoFocus />
      <Button type="button" variant="quiet" onClick={onClose}>Vazgeç</Button>
      <Button type="submit">Ekle</Button>
      {error && <p className="w-full text-[13px] text-tehlike">{error}</p>}
    </form>
  );
}
