// Sermayedarin Kasa sayfasi: sadece kendi parasi. Kasa ve bankalardaki toplam parasi (defterden kesin; hangi hesapta durdugu
// tutulmaz), sermayesinin dagilimi (nakit / aracta / alacak) ve kendi sermaye / cari hareketleri. Islem yok.
// Hareket yonu sermaye acisindan: sermayeden cikan (giris, satis payi) arti, sermayeye giren (cekis, katkinin satisa gecmesi) eksi.
import { api } from "../../api/galeri";
import { tl } from "../../shared/format";
import { useLoad } from "../../shared/useLoad";
import { Empty, ErrorBox } from "../../shared/ui/Common";
import { PartnerCapitalCard } from "../home/PartnerCapitalCard";
import { LedgerEntryList } from "./LedgerEntryList";

export function InvestorLedgerPage() {
  // Sermayedara /api/holders sadece kendi satirini doner: kasa ve bankalardaki parasi backend'den
  const partners = useLoad(() => api.holders(), []);
  const entries = useLoad(() => api.ledgerEntries(null), []);
  const mine = (partners.data ?? []).find((p) => !p.owner);
  const total = mine?.cashCents ?? 0;

  return (
    <div className="space-y-5">
      <header>
        <p className="etiket">Benim param</p>
        <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Kasa ve banka</h1>
      </header>
      <ErrorBox message={partners.error ?? entries.error} />

      <section className="kart p-4 md:p-5">
        <p className="etiket">Kasa ve bankalardaki param</p>
        <p className={`sayi font-bold text-[34px] md:text-[40px] leading-none tracking-tight mt-1.5 ${total < 0 ? "text-tehlike" : ""}`}>
          {mine ? tl(total) : "…"}
        </p>
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
