// Sermayedarin Kasa sayfasi: sadece kendi parasi. Kasa ve bankalardaki toplam parasi ve hesap basina dagilimi (defterden),
// sermayesinin dagilimi (nakit / aracta / alacak) ve kendi sermaye / cari hareketleri. Tek islem: kendi parasini hesaplar arasi
// tasimak (V32).
// Hareket yonu sermaye acisindan: sermayeden cikan (giris, satis payi) arti, sermayeye giren (cekis, katkinin satisa gecmesi) eksi.
import { useState } from "react";
import { api } from "../../api/galeri";
import { tl } from "../../shared/format";
import type { LedgerEntryResponse } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox } from "../../shared/ui/Common";
import { TransferForm } from "./TreasuryForms";
import { PartnerCapitalCard } from "../home/PartnerCapitalCard";
import { LedgerEntryList } from "./LedgerEntryList";

export function InvestorLedgerPage() {
  // Sermayedara /api/holders sadece kendi satirini doner: kasa ve bankalardaki parasi backend'den
  const partners = useLoad(() => api.holders(), []);
  const entries = useLoad(() => api.ledgerEntries(null), []);
  // Sermayedara /api/accounts hesap basina sadece kendi parasini doner
  const accounts = useLoad(() => api.accounts(), []);
  // Kendi taraf oldugu borclar (V33)
  const loans = useLoad(() => api.loans(), []);
  const [transferOpen, setTransferOpen] = useState(false);
  const reload = () => { partners.reload(); entries.reload(); accounts.reload(); loans.reload(); };
  const mine = (partners.data ?? []).find((p) => !p.owner);
  const total = mine?.cashCents ?? 0;
  // Iki sermaye hesabi arasindaki harekette (aktarim, borc) yon kimin hesabinin kaynak oldugundan
  const direction = (e: LedgerEntryResponse) => {
    if (e.source.type === "CAPITAL" && e.target.type === "CAPITAL") return e.source.userId === mine?.userId ? 1 : -1;
    return e.source.type === "CAPITAL" ? 1 : e.target.type === "CAPITAL" ? -1 : 0;
  };

  return (
    <div className="space-y-5">
      <header>
        <p className="etiket">Benim param</p>
        <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Kasa ve banka</h1>
      </header>
      <ErrorBox message={partners.error ?? entries.error ?? loans.error} />

      <section className="kart p-4 md:p-5">
        <p className="etiket">Kasa ve bankalardaki param</p>
        <p className={`sayi font-bold text-[34px] md:text-[40px] leading-none tracking-tight mt-1.5 ${total < 0 ? "text-tehlike" : ""}`}>
          {mine ? tl(total) : "…"}
        </p>
        {(accounts.data ?? []).length > 0 && (
          <ul className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-2">
            {(accounts.data ?? []).map((a) => (
              <li key={a.id} className="rounded-[12px] border border-cizgi px-3 py-2">
                <p className="text-[13px] text-murekkep-2 truncate">{a.name}</p>
                <p className={`sayi font-semibold text-[17px] ${a.balanceCents < 0 ? "text-tehlike" : ""}`}>{tl(a.balanceCents)}</p>
              </li>
            ))}
          </ul>
        )}
        {(accounts.data ?? []).length > 1 && (
          <div className="mt-3">
            <Button variant="secondary" onClick={() => setTransferOpen(true)}>Hesaplar arası taşı</Button>
          </div>
        )}
      </section>
      {transferOpen && <TransferForm investor onClose={() => setTransferOpen(false)} onSaved={() => { setTransferOpen(false); reload(); }} />}

      {(loans.data ?? []).length > 0 && (
        <section className="kart p-4 md:p-5">
          <h2 className="font-cond font-bold text-[22px] mb-2">Borçlar</h2>
          <ul className="divide-y divide-cizgi">
            {(loans.data ?? []).map((l) => {
              const owe = l.borrowerUserId === mine?.userId;
              return (
                <li key={`${l.lenderUserId}-${l.borrowerUserId}`} className="flex items-center justify-between gap-3 py-2.5">
                  <span className="text-[15px]">{owe ? `Borcum · alacaklı ${l.lenderName}` : `Alacağım · borçlu ${l.borrowerName}`}</span>
                  <span className={`sayi font-semibold text-[17px] ${owe ? "text-tehlike" : ""}`}>{tl(l.outstandingCents)}</span>
                </li>
              );
            })}
          </ul>
        </section>
      )}

      <PartnerCapitalCard />

      <section className="kart p-4 md:p-5">
        <h2 className="font-cond font-bold text-[22px] mb-3">Hareketlerim</h2>
        {entries.data && entries.data.length === 0 && <Empty title="Henüz hareket yok" />}
        {entries.data && entries.data.length > 0 && (
          <LedgerEntryList entries={entries.data} direction={direction}
            onChange={reload} readOnly corrections="hide" />
        )}
      </section>
    </div>
  );
}
