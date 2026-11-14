// Parasi yetmeyen sermayedar icin formun icindeki satir: "X TL eksik · Dukkan sahibi borc versin: [Kasa] [Banka] [Cebinden]".
// Kaydet'e basinca islem ve borc tek kayit olur (sunucu eksik kadar borc yazar).
import { tl } from "../../shared/format";
import type { HoldingsResponse } from "../../shared/types";
import { Segment } from "../../shared/ui/Common";
import { ownerPartIn, POCKET } from "./ownerLoan";

export function OwnerLoanPicker({ borrowerName, shortfallCents, holdings, value, onChange }: {
  borrowerName: string;
  shortfallCents: number | null;   // bilinmiyorsa (alista sunucu hesaplar) null
  holdings: HoldingsResponse | null;
  value: string;
  onChange: (value: string) => void;
}) {
  const accounts = holdings?.accounts ?? [];
  return (
    <div className="rounded-[12px] border border-cizgi bg-yuzey-2 px-3 py-3 space-y-2">
      <p className="text-[14px]">
        <span className="font-semibold">{borrowerName}</span>
        {shortfallCents != null
          ? <> için <span className="sayi font-semibold text-tehlike">{tl(shortfallCents)}</span> eksik.</>
          : <> için nakitteki para yetmiyor.</>}
        {" "}Dükkân sahibi borç versin:
      </p>
      <Segment<string> value={value} onChange={onChange}
        options={[
          ...accounts.map((a) => ({ value: a.accountId, label: `${a.name} · ${tl(ownerPartIn(holdings, a.accountId))}` })),
          { value: POCKET, label: "Cebinden" },
        ]} />
      <p className="text-[13px] text-murekkep-2">
        Kaydedince işlem ve borç birlikte yazılır. Cebinden: kasadan / bankadan bir şey düşmez.
      </p>
    </div>
  );
}
