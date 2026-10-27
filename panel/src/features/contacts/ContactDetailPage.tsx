// Kisi detayi: bilgiler + "Cari" (hesap ozeti: hareketler eskiden yeniye, yuruyen bakiye; acik taksitler).
// Arti bakiye bize borclu (alacak, yesil), eksi bizim borcumuz (verecek, kirmizi). Sadece ADMIN.
import { useState } from "react";
import { api } from "../../api/galeri";
import { ENTRY_TYPE, formatPhone, shortDate, tl } from "../../shared/format";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox } from "../../shared/ui/Common";
import { Plate } from "../../shared/ui/Plate";
import { StatTile } from "../../shared/ui/StatTile";
import { InstallmentRow } from "../payments/InstallmentRow";
import { ContactForm } from "./ContactList";
import { ContactPaymentForm } from "./ContactPaymentForm";
import { OpenItemsForm } from "./OpenItemsForm";

export function ContactDetailPage({ id }: { id: string }) {
  const { data, error, reload } = useLoad(() => api.statement(id), [id]);
  const [editing, setEditing] = useState(false);
  const [paying, setPaying] = useState(false);
  const [importing, setImporting] = useState(false);
  const [removeError, setRemoveError] = useState<string | null>(null);

  if (error) return <ErrorBox message={error} />;
  if (!data) return <p className="text-murekkep-2 py-10 text-center">Yükleniyor…</p>;

  const c = data.contact;
  const balance = data.balanceCents;
  const openTotal = data.openInstallments.reduce((t, i) => t + i.amountCents, 0);
  const overdue = data.openInstallments.filter((i) => i.status === "OVERDUE");
  const lines = [...data.lines].reverse();   // ekranda yeniden eskiye
  // Aracsiz (gecmis senet / cek) kayitlar: hic odenmemisse plan butunuyle silinebilir
  const openItemPlans = [...new Set(data.openInstallments.filter((i) => !i.vehicle).map((i) => i.planId))];
  const removePlan = async (planId: string) => {
    if (!window.confirm("Bu geçmiş kayıt (bütün senet / çekleriyle) silinsin mi?")) return;
    setRemoveError(null);
    try {
      await api.removeOpenItems(planId);
      reload();
    } catch (e) {
      setRemoveError(errorMessage(e));
    }
  };

  return (
    <div className="space-y-5">
      <a href="#/contacts" className="inline-flex items-center gap-1 text-murekkep-2 font-semibold text-[15px] hover:text-murekkep">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M15 6l-6 6 6 6" /></svg>
        Kişiler
      </a>

      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="etiket">{c.type === "ORGANIZATION" ? "Kurum" : "Kişi"}</p>
          <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">{c.name}</h1>
          <p className="sayi text-[17px] text-murekkep-2 mt-0.5">{formatPhone(c.phone) || "Telefon yok"}</p>
          {c.notes && <p className="text-[14px] text-murekkep-3 mt-1 max-w-xl">{c.notes}</p>}
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setEditing(true)}>Düzenle</Button>
          <Button variant="secondary" onClick={() => setImporting(true)}>Geçmiş senet / çek</Button>
          <Button variant={balance < 0 ? "primary" : "secondary"} onClick={() => setPaying(true)}>Öde</Button>
        </div>
      </header>

      <div className="grid grid-cols-2 lg:grid-cols-3 gap-3">
        <StatTile label={balance >= 0 ? "Alacağımız" : "Borcumuz"} value={tl(Math.abs(balance))}
          tone={balance > 0 ? "positive" : balance < 0 ? "negative" : "neutral"}
          sub={balance > 0 ? "Bize borçlu" : balance < 0 ? "Biz borçluyuz" : "Hesap kapalı"} />
        <StatTile label="Açık taksit" value={tl(openTotal)} tone="brand" sub={`${data.openInstallments.length} taksit`} />
        <StatTile label="Geciken" value={tl(overdue.reduce((t, i) => t + i.amountCents, 0))} tone={overdue.length ? "negative" : "neutral"}
          sub={overdue.length ? `${overdue.length} taksit` : "Yok"} />
      </div>

      {data.openInstallments.length > 0 && (
        <section className="kart p-4 md:p-5">
          <h2 className="font-cond font-bold text-[21px] tracking-tight pb-3 mb-1 border-b border-cizgi">Açık taksitler</h2>
          <ul className="divide-y divide-cizgi -mx-1">
            {data.openInstallments.map((i) => <InstallmentRow key={i.id} i={i} onChange={reload} showPayer={false} />)}
          </ul>
          <ErrorBox message={removeError} />
          {openItemPlans.map((planId) => {
            const items = data.openInstallments.filter((i) => i.planId === planId);
            return (
              <div key={planId} className="flex items-center justify-between gap-2 pt-3 text-[14px] text-murekkep-2">
                <span>Geçmiş kayıt · {items.length} adet · <b className="sayi text-murekkep">{tl(items.reduce((t, i) => t + i.amountCents, 0))}</b></span>
                <Button variant="quiet" className="h-9 text-tehlike" onClick={() => removePlan(planId)}>Kaydı sil</Button>
              </div>
            );
          })}
        </section>
      )}

      <section className="kart overflow-hidden">
        <h2 className="font-cond font-bold text-[21px] tracking-tight px-4 md:px-5 pt-4 pb-3 border-b border-cizgi">Cari hesap özeti</h2>
        {lines.length === 0
          ? <Empty title="Hareket yok" description="Satış ya da senetli alış yapılınca cari burada görünür." />
          : (
            <ul className="divide-y divide-cizgi">
              {lines.map((l) => (
                <li key={l.entryId} className="flex items-center gap-3 px-4 md:px-5 py-3">
                  <span className="w-14 shrink-0 text-[13px] font-medium text-murekkep-2">{shortDate(l.date)}</span>
                  <span className="min-w-0 grow">
                    <span className={`block font-semibold text-[15px] ${l.reversal ? "text-murekkep-3" : ""}`}>
                      {l.description ?? ENTRY_TYPE[l.type]}
                      {l.reversal && <span className="ml-2 text-[11.5px] font-semibold rounded px-1.5 py-0.5 bg-yuzey-2 border border-cizgi">Düzeltme</span>}
                    </span>
                    {l.vehicle && (
                      <a href={`#/vehicles/${l.vehicle.id}`} className="mt-1 inline-flex items-center gap-2 text-[13px] text-murekkep-2 hover:text-murekkep">
                        <Plate plate={l.vehicle.plate} size="sm" /><span className="truncate">{l.vehicle.make} {l.vehicle.model}</span>
                      </a>
                    )}
                  </span>
                  <span className="text-right shrink-0">
                    <span className={`block sayi font-semibold text-[17px] ${
                      !l.reversal && (l.type === "COLLECTION" || l.type === "PAYMENT") ? "text-basari" : "text-murekkep"}`}>
                      {l.amountCents > 0 ? "+" : "−"} {tl(Math.abs(l.amountCents))}
                    </span>
                    <span className="block text-[12px] text-murekkep-3">bakiye <span className="sayi text-[13px]">{tl(l.runningCents)}</span></span>
                  </span>
                </li>
              ))}
            </ul>
          )}
      </section>

      {paying && <ContactPaymentForm contact={c} debtCents={balance < 0 ? -balance : 0} onClose={() => setPaying(false)}
        onSaved={() => { setPaying(false); reload(); }} />}
      {importing && <OpenItemsForm contact={c} onClose={() => setImporting(false)} onSaved={() => { setImporting(false); reload(); }} />}
      {editing && <ContactForm existing={c} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); reload(); }} />}
    </div>
  );
}
