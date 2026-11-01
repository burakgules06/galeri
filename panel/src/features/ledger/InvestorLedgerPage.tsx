// Sermayedarin Kasa sayfasi: sadece kendi parasi. Kasa ve bankalarda ona dusen tutar (hesap bakiyesi ve baskalarinin parasi
// gorunmez), sermayesinin dagilimi (nakit / aracta / alacak) ve kendi sermaye / cari hareketleri. Islem yok.
// Hareket yonu sermaye acisindan: sermayeden cikan (giris, satis payi) arti, sermayeye giren (cekis, katkinin satisa gecmesi) eksi.
import { api } from "../../api/galeri";
import { SERIES, tl } from "../../shared/format";
import { useLoad } from "../../shared/useLoad";
import { Empty, ErrorBox } from "../../shared/ui/Common";
import { PartnerCapitalCard } from "../home/PartnerCapitalCard";
import { LedgerEntryList } from "./LedgerEntryList";

export function InvestorLedgerPage() {
  const holdings = useLoad(() => api.holdings(), []);
  const entries = useLoad(() => api.ledgerEntries(null), []);
  const list = holdings.data ?? [];
  const total = list.reduce((t, h) => t + h.balanceCents, 0);

  return (
    <div className="space-y-5">
      <header>
        <p className="etiket">Benim param</p>
        <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Kasa ve banka</h1>
      </header>
      <ErrorBox message={holdings.error ?? entries.error} />

      <section className="kart p-4 md:p-5">
        <p className="etiket">Kasa ve bankalardaki param</p>
        <p className={`sayi font-bold text-[34px] md:text-[40px] leading-none tracking-tight mt-1.5 ${total < 0 ? "text-tehlike" : ""}`}>
          {holdings.data ? tl(total) : "…"}
        </p>
        <div className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-2">
          {list.map((h, i) => (
            <div key={h.accountId} className="rounded-[12px] border border-cizgi px-3.5 py-2.5">
              <span className="flex items-center gap-1.5 text-[13px] font-semibold text-murekkep-2 truncate">
                <span className="w-2.5 h-2.5 rounded-[3px] shrink-0" style={{ background: SERIES[i % SERIES.length] }} />{h.name}
              </span>
              <span className={`block sayi font-bold text-[20px] leading-tight mt-0.5 ${h.balanceCents < 0 ? "text-tehlike" : ""}`}>{tl(h.balanceCents)}</span>
            </div>
          ))}
        </div>
      </section>

      <PartnerCapitalCard />

      <section className="kart p-4 md:p-5">
        <h2 className="font-cond font-bold text-[22px] mb-3">Hareketlerim</h2>
        {entries.data && entries.data.length === 0 && <Empty title="Henüz hareket yok" />}
        {entries.data && entries.data.length > 0 && (
          <LedgerEntryList entries={entries.data} direction={(e) => (e.source.type === "CAPITAL" ? 1 : e.target.type === "CAPITAL" ? -1 : 0)}
            onChange={entries.reload} readOnly corrections="hide" />
        )}
      </section>
    </div>
  );
}
