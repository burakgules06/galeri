// Ortak arac satisinda ortaga donus: her tahsilat kar payi oranlariyla bolunur. Simdi alinan kisimdan ortagin payi sermayesine
// gecer; burada sermayede mi kalsin, nakit mi odensin secilir. Kalan tahsil edildikce gelir. Tutarlar tahmin, kesini kayitta.
import { tl } from "../../shared/format";
import type { AccountResponse, CommissionType, OwnerReturnMode, ShareResponse } from "../../shared/types";
import { inputClass, Segment } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";
import { ownerSaleShares } from "./coOwners";
import type { OwnerReturnDraft } from "./ownerReturnDraft";

interface Props {
  owners: ShareResponse[];
  saleCents: number;
  paidNowCents: number;
  purchaseCents: number;
  expenseCents: number;
  shopCutType: CommissionType | null;
  shopCutRateBp: number | null;
  shopCutCents: number | null;
  accounts: AccountResponse[];
  value: Record<string, OwnerReturnDraft>;
  onChange: (v: Record<string, OwnerReturnDraft>) => void;
}

const MODES: { value: OwnerReturnMode; label: string }[] = [
  { value: "KEEP", label: "Sermayede kalsın" }, { value: "CASH", label: "Nakit öde" }, { value: "HYBRID", label: "Hibrit" },
];

export function OwnerReturns({ owners, saleCents, paidNowCents, purchaseCents, expenseCents, shopCutType, shopCutRateBp, shopCutCents,
  accounts, value, onChange }: Props) {
  const partners = owners.filter((o) => !o.owner);
  if (partners.length === 0) return null;
  const shares = saleCents > 0 ? ownerSaleShares(saleCents, paidNowCents, purchaseCents, expenseCents, owners, shopCutType, shopCutRateBp, shopCutCents) : null;
  return (
    <section className="space-y-3">
      <div>
        <p className="etiket">Ortaklar</p>
        <p className="text-[13px] text-murekkep-2 mt-0.5">Her tahsilat kâr payı oranıyla bölünür. Şimdi alınan paradan ortağın payı sermayesine geçer; kalanı tahsil edildikçe.</p>
      </div>

      {partners.map((o) => {
        const draft = value[o.userId] ?? { mode: "KEEP", amount: "", accountId: accounts[0]?.id ?? "" };
        const share = shares?.[o.userId];
        const set = (change: Partial<OwnerReturnDraft>) => onChange({ ...value, [o.userId]: { ...draft, ...change } });
        return (
          <div key={o.userId} className="border-l-[3px] border-ortakli pl-3 py-1 space-y-2">
            <p className="text-[14px]">
              <span className="font-semibold">{o.name}</span>
              <span className="text-murekkep-2"> · kâr payı <b className="sayi text-[15px] text-ortakli">%{(o.profitShareBp / 100).toLocaleString("tr-TR")}</b></span>
              <span className="text-murekkep-2"> · koyduğu <b className="sayi text-[15px] text-murekkep">{tl(o.contributionCents)}</b></span>
              {share && <span className="text-murekkep-2"> · alacağı yaklaşık <b className="sayi text-[15px] text-murekkep">{tl(share.entitled)}</b>,
                şimdi <b className="sayi text-[15px] text-basari">{tl(share.now)}</b></span>}
            </p>
            {share != null && share.now <= 0
              ? <p className="text-[13px] text-murekkep-2">Şimdi alınan paradan payı yok; tahsil edildikçe sermayesine geçer.</p>
              : <div className="flex flex-wrap items-center gap-2">
                  <Segment<OwnerReturnMode> value={draft.mode} onChange={(mode) => set({ mode })} options={MODES} />
                  {draft.mode === "HYBRID" && (
                    <NumberInput className={`${inputClass} w-40`} value={draft.amount} onValueChange={(amount) => set({ amount })}
                      placeholder="Nakit TL" aria-label={`${o.name} nakit tutar`} />
                  )}
                  {draft.mode !== "KEEP" && (
                    <select className={`${inputClass} w-auto`} value={draft.accountId} onChange={(e) => set({ accountId: e.target.value })}
                      aria-label={`${o.name} ödeme hesabı`}>
                      {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
                    </select>
                  )}
                </div>}
          </div>
        );
      })}
    </section>
  );
}
