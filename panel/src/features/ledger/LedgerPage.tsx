// Kasa ve banka: bakiyeler (hareketlerden hesaplanir) ve hareket listesi. Sadece ADMIN.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { entryDirection, tl } from "../../shared/format";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox, inputClass } from "../../shared/ui/Common";
import { ExpenseForm } from "./ExpenseForm";
import { LedgerEntryList } from "./LedgerEntryList";

export function LedgerPage() {
  const [selected, setSelected] = useState<string | null>(null);
  const [expenseOpen, setExpenseOpen] = useState(false);
  const [bankOpen, setBankOpen] = useState(false);
  const accounts = useLoad(() => api.accounts(), []);
  const entries = useLoad(() => api.ledgerEntries(selected), [selected]);

  const reload = () => { accounts.reload(); entries.reload(); };
  const total = (accounts.data ?? []).reduce((t, a) => t + a.balanceCents, 0);
  const selectedName = accounts.data?.find((a) => a.id === selected)?.name;

  return (
    <div>
      <div className="flex items-end justify-between gap-3 mb-4">
        <div>
          <h1 className="font-cond font-bold text-[30px] leading-none tracking-tight">Kasa ve banka</h1>
          <p className="text-murekkep-2 mt-1">Toplam <span className="font-cond font-semibold text-[18px] text-murekkep">{accounts.data ? tl(total) : "…"}</span></p>
        </div>
        <Button onClick={() => setExpenseOpen(true)}>Masraf ekle</Button>
      </div>

      <ErrorBox message={accounts.error} />

      <div className="grid grid-cols-2 md:grid-cols-4 gap-2 mb-5">
        <AccountCard name="Tümü" balance={total} selected={selected === null} onClick={() => setSelected(null)} />
        {(accounts.data ?? []).map((a) => (
          <AccountCard key={a.id} name={a.name} balance={a.balanceCents} selected={selected === a.id} onClick={() => setSelected(a.id)} />
        ))}
        {bankOpen
          ? <BankForm onClose={() => setBankOpen(false)} onAdded={() => { setBankOpen(false); accounts.reload(); }} />
          : (
            <button type="button" onClick={() => setBankOpen(true)}
              className="basilir rounded-xl border border-dashed border-cizgi-2 text-murekkep-2 font-semibold text-[15px] min-h-[76px] hover:bg-yuzey">
              + Banka hesabı
            </button>
          )}
      </div>

      <section className="bg-yuzey rounded-xl border border-cizgi p-4 md:p-5">
        <h2 className="font-cond font-semibold text-[20px] mb-3">{selectedName ? `${selectedName} hareketleri` : "Son hareketler"}</h2>
        <ErrorBox message={entries.error} />
        {entries.data && entries.data.length === 0 && (
          <Empty title="Henüz hareket yok" description="Masraf girdiğinde ya da kasadan araç aldığında burada görünür." />
        )}
        {entries.data && entries.data.length > 0 && (
          <LedgerEntryList entries={entries.data} direction={(e) => entryDirection(e, selected)} onChange={reload} />
        )}
      </section>

      <ExpenseForm open={expenseOpen} onClose={() => setExpenseOpen(false)} onSaved={() => { setExpenseOpen(false); reload(); }} />
    </div>
  );
}

function AccountCard({ name, balance, selected, onClick }: { name: string; balance: number; selected: boolean; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} aria-pressed={selected}
      className={`basilir text-left rounded-xl border px-4 py-3 min-h-[76px] ${selected ? "bg-plaka-acik border-plaka" : "bg-yuzey border-cizgi hover:bg-yuzey-2"}`}>
      <span className={`block text-[14px] font-semibold truncate ${selected ? "text-plaka-koyu" : "text-murekkep-2"}`}>{name}</span>
      <span className={`block font-cond font-bold text-[22px] leading-tight ${balance < 0 ? "text-tehlike" : "text-murekkep"}`}>{tl(balance)}</span>
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
    <form onSubmit={submit} className="rounded-xl border border-cizgi bg-yuzey p-2 space-y-1.5 col-span-2 md:col-span-1">
      <input className={`${inputClass} h-9`} value={name} onChange={(e) => setName(e.target.value)} placeholder="Banka adı" autoFocus />
      <div className="flex gap-1.5">
        <Button type="button" variant="quiet" className="h-8 grow" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" className="h-8 grow">Ekle</Button>
      </div>
      {error && <p className="text-[13px] text-tehlike">{error}</p>}
    </form>
  );
}
