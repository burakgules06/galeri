// Kasa: nakit (kasa + bankalar) ve uc sekme: hareketler (ortak payi, alacak / verecek, hareket listesi),
// ortaklar (sermaye girisi / cekisi, dukkan sahibi), donem kari (kar payini sermayeye ekleme). Sadece ADMIN.
// Bakiyeler ve paylar hareketlerden hesaplanir; ortak ve cari ozeti /api/dashboard'dan gelir.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { SplitBar } from "../../shared/charts/Donut";
import { entryDirection, percent, SERIES, tl } from "../../shared/format";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox, inputClass, Segment } from "../../shared/ui/Common";
import { BalancesCard } from "./BalancesCard";
import { CapitalCard } from "./CapitalCard";
import { ExpenseForm } from "./ExpenseForm";
import { LedgerEntryList } from "./LedgerEntryList";
import { PartnersPanel } from "./PartnersPanel";
import { PeriodProfitPanel } from "./PeriodProfitPanel";
import { OpeningBalanceForm, TransferForm } from "./TreasuryForms";

type Tab = "entries" | "partners" | "profit";

export function LedgerPage() {
  const [selected, setSelected] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("entries");
  const [expenseOpen, setExpenseOpen] = useState(false);
  const [bankOpen, setBankOpen] = useState(false);
  const [dialog, setDialog] = useState<"transfer" | "opening" | null>(null);
  const accounts = useLoad(() => api.accounts(), []);
  const summary = useLoad(() => api.dashboard(), []);
  const entries = useLoad(() => api.ledgerEntries(selected), [selected]);

  const reload = () => { accounts.reload(); entries.reload(); summary.reload(); };
  const list = accounts.data ?? [];
  const total = list.reduce((t, a) => t + a.balanceCents, 0);
  const selectedName = list.find((a) => a.id === selected)?.name;
  const colored = list.map((a, i) => ({ ...a, color: SERIES[i % SERIES.length] }));

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
          <AccountButton name="Tümü" balance={total} share={null} color={null} selected={selected === null} onClick={() => setSelected(null)} />
          {colored.map((a) => (
            <AccountButton key={a.id} name={a.name} balance={a.balanceCents} color={a.color}
              share={total > 0 && a.balanceCents > 0 ? Math.round((a.balanceCents * 10000) / total) : null}
              selected={selected === a.id} onClick={() => setSelected(a.id)} />
          ))}
        </div>
      </section>

      <Segment<Tab> value={tab} onChange={setTab} options={[
        { value: "entries", label: "Hareketler" }, { value: "partners", label: "Ortaklar ve sermaye" }, { value: "profit", label: "Dönem kârı" },
      ]} />

      {tab === "partners" && <PartnersPanel onChange={reload} />}
      {tab === "profit" && <PeriodProfitPanel onChange={reload} />}

      {tab === "entries" && summary.data && (
        <div className="grid gap-4 lg:grid-cols-2">
          <CapitalCard partners={summary.data.partners} totalCents={summary.data.capitalCents} />
          <BalancesCard receivableCents={summary.data.receivableCents} payableCents={summary.data.payableCents}
            receivables={summary.data.receivables} payables={summary.data.payables} />
        </div>
      )}

      {tab === "entries" && <section className="kart p-4 md:p-5">
        <div className="flex items-baseline justify-between gap-3 mb-3">
          <h2 className="font-cond font-bold text-[22px]">{selectedName ? `${selectedName} hareketleri` : "Son hareketler"}</h2>
          {entries.data && <span className="text-[13px] text-murekkep-2">{entries.data.length} kayıt</span>}
        </div>
        <ErrorBox message={entries.error} />
        {entries.data && entries.data.length === 0 && (
          <Empty title="Henüz hareket yok" description="Masraf girdiğinde ya da kasadan araç aldığında burada görünür." />
        )}
        {entries.data && entries.data.length > 0 && (
          <LedgerEntryList entries={entries.data} direction={(e) => entryDirection(e, selected)} onChange={reload} />
        )}
      </section>}

      {dialog === "transfer" && <TransferForm onClose={() => setDialog(null)} onSaved={() => { setDialog(null); reload(); }} />}
      {dialog === "opening" && <OpeningBalanceForm onClose={() => setDialog(null)} onSaved={() => { setDialog(null); reload(); }} />}
      <ExpenseForm open={expenseOpen} onClose={() => setExpenseOpen(false)} onSaved={() => { setExpenseOpen(false); reload(); }} />
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
