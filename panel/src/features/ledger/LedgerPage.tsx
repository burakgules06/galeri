// Kasa: nakit (kasa + bankalar) ve uc sekme: hareketler (ortak payi, alacak / verecek, hareket listesi),
// ortaklar (sermaye girisi / cekisi, dukkan sahibi), donem kari (kar payini sermayeye ekleme). Sadece ADMIN.
// Bakiyeler ve paylar hareketlerden hesaplanir; ortak ve cari ozeti /api/dashboard'dan gelir.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { SplitBar } from "../../shared/charts/Donut";
import { ENTRY_TYPE, entryDirection, percent, SERIES, tl } from "../../shared/format";
import type { LedgerEntryType } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox, inputClass, Segment } from "../../shared/ui/Common";
import { BalancesCard } from "./BalancesCard";
import { PartnerCapitalCard } from "../home/PartnerCapitalCard";
import { BulkExpenseForm } from "./BulkExpenseForm";
import { LedgerEntryList } from "./LedgerEntryList";
import { PartnersPanel } from "./PartnersPanel";
import { PeriodProfitPanel } from "./PeriodProfitPanel";
import { NetWorthPanel } from "./NetWorthPanel";
import { OpeningBalanceForm, TransferForm } from "./TreasuryForms";

type Tab = "entries" | "partners" | "profit" | "assets";

export function LedgerPage() {
  const [selected, setSelected] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("entries");
  const [revision, setRevision] = useState(0);
  const [expenseOpen, setExpenseOpen] = useState(false);
  const [bankOpen, setBankOpen] = useState(false);
  const [dialog, setDialog] = useState<"transfer" | "opening" | null>(null);
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [person, setPerson] = useState("");
  const [entryType, setEntryType] = useState<LedgerEntryType | "">("");
  const [entryPage, setEntryPage] = useState(1);
  const accounts = useLoad(() => api.accounts(), []);
  const summary = useLoad(() => api.dashboard(), []);
  const entries = useLoad(() => api.ledgerEntries(selected), [selected]);

  const reload = () => { accounts.reload(); entries.reload(); summary.reload(); setRevision(r => r + 1); };
  const list = accounts.data ?? [];
  const total = list.reduce((t, a) => t + a.balanceCents, 0);
  const selectedName = list.find((a) => a.id === selected)?.name;
  const colored = list.map((a, i) => ({ ...a, color: SERIES[i % SERIES.length] }));
  const allEntries = entries.data ?? [];
  const people = [...new Set(allEntries.flatMap((e) => [e.source.name, e.target.name]))].sort((a, b) => a.localeCompare(b, "tr"));
  const filteredEntries = allEntries.filter((e) =>
    (!fromDate || e.date >= fromDate) && (!toDate || e.date <= toDate) &&
    (!person || e.source.name === person || e.target.name === person) && (!entryType || e.type === entryType));
  const hasEntryFilters = !!(fromDate || toDate || person || entryType);
  const entryPageSize = 20;
  const entryPageCount = Math.max(1, Math.ceil(filteredEntries.length / entryPageSize));
  const currentEntryPage = Math.min(entryPage, entryPageCount);
  const pageEntries = filteredEntries.slice((currentEntryPage - 1) * entryPageSize, currentEntryPage * entryPageSize);
  const changeAccount = (id: string | null) => { setSelected(id); setEntryPage(1); };
  const resetEntryFilters = () => { setFromDate(""); setToDate(""); setPerson(""); setEntryType(""); setEntryPage(1); };

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="etiket">Para defteri</p>
          <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Kasa ve banka</h1>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => setDialog("opening")}>Açılış bakiyesi</Button>
          <Button variant="secondary" onClick={() => setDialog("transfer")}>Virman</Button>
          <Button onClick={() => setExpenseOpen(true)}>Masraf ekle</Button>
        </div>
      </header>

      <ErrorBox message={accounts.error ?? summary.error} />

      {/* Nakit: toplam ve hesaplara dagilimi. Karta dokununca hareketler o hesaba suzulur. */}
      <section className="kart p-4 md:p-5">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="etiket">Toplam nakit</p>
            <p className={`sayi font-bold text-[34px] md:text-[40px] leading-none tracking-tight mt-1.5 ${total < 0 ? "text-tehlike" : ""}`}>
              {accounts.data ? tl(total) : "…"}
            </p>
          </div>
          {bankOpen
            ? <BankForm onClose={() => setBankOpen(false)} onAdded={() => { setBankOpen(false); accounts.reload(); }} />
            : <Button variant="secondary" onClick={() => setBankOpen(true)}>+ Banka hesabı</Button>}
        </div>
        <div className="mt-4"><SplitBar height={10} parts={colored.map((a) => ({ key: a.id, value: a.balanceCents, color: a.color }))} /></div>
        <div className="mt-3 grid grid-cols-2 md:grid-cols-4 gap-2">
          <AccountButton name="Tümü" balance={total} share={null} color={null} selected={selected === null} onClick={() => changeAccount(null)} />
          {colored.map((a) => (
            <AccountButton key={a.id} name={a.name} balance={a.balanceCents} color={a.color}
              share={total > 0 && a.balanceCents > 0 ? Math.round((a.balanceCents * 10000) / total) : null}
              selected={selected === a.id} onClick={() => changeAccount(a.id)} />
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
        <div className="flex items-baseline justify-between gap-3 mb-3">
          <h2 className="font-cond font-bold text-[22px]">{selectedName ? `${selectedName} hareketleri` : "Son hareketler"}</h2>
          {entries.data && <span className="text-[13px] text-murekkep-2">{filteredEntries.length}{hasEntryFilters ? ` / ${allEntries.length}` : ""} kayıt</span>}
        </div>
        <ErrorBox message={entries.error} />
        {entries.data && allEntries.length > 0 && <div className="mb-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-[150px_150px_minmax(180px,1fr)_minmax(180px,1fr)_auto] xl:items-end">
          <label className="text-[12px] text-murekkep-2">Başlangıç tarihi<input type="date" className={`${inputClass} mt-1 h-9 w-full`} value={fromDate} max={toDate || undefined} onChange={(e) => { setFromDate(e.target.value); setEntryPage(1); }} /></label>
          <label className="text-[12px] text-murekkep-2">Bitiş tarihi<input type="date" className={`${inputClass} mt-1 h-9 w-full`} value={toDate} min={fromDate || undefined} onChange={(e) => { setToDate(e.target.value); setEntryPage(1); }} /></label>
          <label className="text-[12px] text-murekkep-2">Kişi<select className={`${inputClass} mt-1 h-9 w-full`} value={person} onChange={(e) => { setPerson(e.target.value); setEntryPage(1); }}>
            <option value="">Tüm kişiler</option>{people.map((name) => <option key={name} value={name}>{name}</option>)}
          </select></label>
          <label className="text-[12px] text-murekkep-2">Hareket tipi<select className={`${inputClass} mt-1 h-9 w-full`} value={entryType} onChange={(e) => { setEntryType(e.target.value as LedgerEntryType | ""); setEntryPage(1); }}>
            <option value="">Tüm hareketler</option>{Object.entries(ENTRY_TYPE).map(([type, label]) => <option key={type} value={type}>{label}</option>)}
          </select></label>
          {hasEntryFilters && <Button type="button" variant="quiet" className="h-9" onClick={resetEntryFilters}>Filtreleri temizle</Button>}
        </div>}
        {entries.data && allEntries.length === 0 && (
          <Empty title="Henüz hareket yok" description="Masraf girdiğinde ya da kasadan araç aldığında burada görünür." />
        )}
        {entries.data && allEntries.length > 0 && filteredEntries.length === 0 && (
          <Empty title="Filtreye uygun hareket yok" description="Tarih aralığını, kişiyi veya hareket tipini değiştirebilirsin." />
        )}
        {entries.data && filteredEntries.length > 0 && (
          <LedgerEntryList entries={pageEntries} direction={(e) => entryDirection(e, selected)} onChange={reload} />
        )}
        {entries.data && filteredEntries.length > entryPageSize && <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-cizgi pt-3">
          <span className="text-[13px] text-murekkep-2">{(currentEntryPage - 1) * entryPageSize + 1}–{Math.min(currentEntryPage * entryPageSize, filteredEntries.length)} / {filteredEntries.length} hareket</span>
          <div className="flex items-center gap-2">
            <Button type="button" variant="secondary" className="h-9" disabled={currentEntryPage === 1} onClick={() => setEntryPage((p) => Math.max(1, p - 1))}>Önceki</Button>
            <span className="min-w-16 text-center text-[13px] font-semibold">{currentEntryPage} / {entryPageCount}</span>
            <Button type="button" variant="secondary" className="h-9" disabled={currentEntryPage === entryPageCount} onClick={() => setEntryPage((p) => Math.min(entryPageCount, p + 1))}>Sonraki</Button>
          </div>
        </div>}
      </section>}

      {dialog === "transfer" && <TransferForm onClose={() => setDialog(null)} onSaved={() => { setDialog(null); reload(); }} />}
      {dialog === "opening" && <OpeningBalanceForm onClose={() => setDialog(null)} onSaved={() => { setDialog(null); reload(); }} />}
      {expenseOpen && <BulkExpenseForm onClose={() => setExpenseOpen(false)} onSaved={() => { setExpenseOpen(false); reload(); }} />}
    </div>
  );
}

function AccountButton({ name, balance, share, color, selected, onClick }: {
  name: string; balance: number; share: number | null; color: string | null; selected: boolean; onClick: () => void;
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
