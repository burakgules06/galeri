// Kisiler arasi borc (V33): borc ver ya da geri ode. Para kasada / bankada kalir; secilen hesapta kimin parasi oldugu degisir.
// Borc vermede verenin, geri odemede borclunun o hesaptaki parasi gosterilir (dukkan sahibi icin hesabin kalani, sinir yok).
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { centsToInput, tl, tlToCents, todayIso } from "../../shared/format";
import type { Partner } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";

export function LoanForm({ mode, partners, lenderId, borrowerId, maxCents, onClose, onSaved }: {
  mode: "lend" | "repay";
  partners: Partner[];
  lenderId?: string;
  borrowerId?: string;
  maxCents?: number;          // geri odemede kalan borc
  onClose: () => void;
  onSaved: () => void;
}) {
  const repay = mode === "repay";
  const owner = partners.find((p) => p.owner);
  const [lender, setLender] = useState(lenderId ?? owner?.userId ?? partners[0]?.userId ?? "");
  const [borrower, setBorrower] = useState(borrowerId ?? partners.find((p) => p.userId !== (lenderId ?? owner?.userId))?.userId ?? "");
  const [amount, setAmount] = useState(repay && maxCents ? centsToInput(maxCents) : "");
  const [accountId, setAccountId] = useState("");
  const [date, setDate] = useState(todayIso());
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const holdings = useLoad(() => api.holdings(), []);

  // Parayi veren taraf: borcta veren, odemede borclu
  const payerId = repay ? borrower : lender;
  const payer = partners.find((p) => p.userId === payerId);
  const accounts = holdings.data?.accounts ?? [];
  const amountIn = (accountId: string) => {
    const h = accounts.find((a) => a.accountId === accountId);
    if (!h) return 0;
    return payer?.owner ? h.balanceCents
      : h.parts.filter((p) => !p.owner && p.userId === payerId).reduce((t, p) => t + p.cents, 0);
  };
  // Varsayilan hesap: parayi verenin en cok parasinin oldugu hesap
  const best = [...accounts].sort((a, b) => amountIn(b.accountId) - amountIn(a.accountId))[0]?.accountId ?? "";
  const selected = accountId || best;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const cents = tlToCents(amount);
    if (cents == null || Number.isNaN(cents) || cents <= 0) return setError("Tutarı 100.000 gibi yaz.");
    if (!lender || !borrower || lender === borrower) return setError("Borç veren ve alan farklı kişiler olmalı.");
    if (!selected) return setError("Hesap seç.");
    if (repay && maxCents != null && cents > maxCents) return setError(`Kalan borç ${tl(maxCents)}.`);
    if (payer && !payer.owner && cents > amountIn(selected)) {
      return setError(`${payer.name}: bu hesapta ${tl(Math.max(0, amountIn(selected)))} parası var.`);
    }
    setSaving(true);
    setError(null);
    try {
      const request = { lenderUserId: lender, borrowerUserId: borrower, accountId: selected, date, amountCents: cents,
        description: description.trim() || null };
      await (repay ? api.repayLoan(request) : api.lend(request));
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const nameOf = (id: string) => partners.find((p) => p.userId === id)?.name ?? "";
  return (
    <Modal title={repay ? `${nameOf(borrower)} · borç ödemesi` : "Borç ver"} open onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="loan-form" disabled={saving}>{saving ? "Kaydediliyor…" : repay ? "Öde" : "Borç ver"}</Button>
      </>}>
      <form id="loan-form" onSubmit={submit} className="space-y-4">
        <ErrorBox message={error} />
        {repay ? (
          <p className="text-[14px] text-murekkep-2">
            Alacaklı: <span className="font-semibold text-murekkep">{nameOf(lender)}</span> · kalan borç{" "}
            <span className="sayi font-semibold text-murekkep">{tl(maxCents ?? 0)}</span>.
            Ödenen tutar seçilen hesapta alacaklının parası olur.
          </p>
        ) : (
          <div className="grid sm:grid-cols-2 gap-3">
            <Field label="Borç veren">
              <select className={inputClass} value={lender} onChange={(e) => { setLender(e.target.value); setAccountId(""); }}>
                {partners.map((p) => <option key={p.userId} value={p.userId}>{p.name}{p.owner ? " (dükkân sahibi)" : ""}</option>)}
              </select>
            </Field>
            <Field label="Borç alan">
              <select className={inputClass} value={borrower} onChange={(e) => setBorrower(e.target.value)}>
                {partners.filter((p) => p.userId !== lender).map((p) => <option key={p.userId} value={p.userId}>{p.name}</option>)}
              </select>
            </Field>
          </div>
        )}
        <Field label="Tutar">
          <NumberInput className={`${inputClass} font-cond text-[18px]`} value={amount} onValueChange={setAmount} placeholder="100.000" autoFocus />
        </Field>
        <Field label={`Hangi hesaptaki parasından (${payer?.name ?? ""})`}>
          {holdings.data && (
            <Segment<string> value={selected} onChange={setAccountId}
              options={accounts.map((a) => ({ value: a.accountId, label: `${a.name} · ${tl(amountIn(a.accountId))}` }))} />
          )}
          <p className="text-[13px] text-murekkep-2 mt-1">Para hesaptan çıkmaz; o hesapta kimin parası olduğu değişir.</p>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Tarih"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} required /></Field>
          <Field label="Açıklama"><input className={inputClass} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="İsteğe bağlı" /></Field>
        </div>
      </form>
    </Modal>
  );
}
