// Ortak arac satisinda ortaga donus: her ortak icin alacagi (katki + kar payi) sermayede mi kalsin, nakit mi odensin.
// Alacak satis aninda yazilir (alicinin senetleri beklenmez); burada gosterilen tahmindir, kesin tutar kayitta hesaplanir.
import { tl } from "../../shared/format";
import type { AccountResponse, CommissionType, OwnerReturnMode, ShareResponse } from "../../shared/types";
import { inputClass, Segment } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";
import { ownerReceivables } from "./coOwners";
import type { OwnerReturnDraft } from "./ownerReturnDraft";

interface Props {
  owners: ShareResponse[];
  /** Tahmini kar (satis - maliyet); satis bedeli girilmediyse null */
  profitCents: number | null;
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

export function OwnerReturns({ owners, profitCents, shopCutType, shopCutRateBp, shopCutCents, accounts, value, onChange }: Props) {
  const partners = owners.filter((o) => o.contactId);
  if (partners.length === 0) return null;
  const receivables = profitCents == null ? null : ownerReceivables(profitCents, owners, shopCutType, shopCutRateBp, shopCutCents);
  return (
    <section className="space-y-3">
      <p className="etiket">Ortağa dönüş</p>
      <p className="text-[13px] text-murekkep-2">
        Ortağın alacağı (katkısı + kâr payı) satışla birlikte yazılır, alıcının senetleri beklenmez. Nakit kısım kasadan ya da
        bankadan sermaye çekişi olarak ödenir.
      </p>
      {partners.map((o) => {
        const draft = value[o.contactId!] ?? { mode: "KEEP", amount: "", accountId: accounts[0]?.id ?? "" };
        const receivable = receivables?.[o.contactId!];
        const set = (change: Partial<OwnerReturnDraft>) => onChange({ ...value, [o.contactId!]: { ...draft, ...change } });
        return (
          <div key={o.contactId} className="border-l-[3px] border-ortakli pl-3 py-1 space-y-2">
            <p className="text-[14px]">
              <span className="font-semibold">{o.name}</span>
              <span className="text-murekkep-2"> · katkı <b className="sayi text-[15px] text-murekkep">{tl(o.contributionCents)}</b></span>
              {receivable != null && <span className="text-murekkep-2"> · {receivable < 0 ? "galeriye borcu" : "alacağı"} yaklaşık <b className={`sayi text-[15px] ${receivable < 0 ? "text-tehlike" : "text-murekkep"}`}>{tl(Math.abs(receivable))}</b></span>}
            </p>
            {receivable != null && receivable <= 0
              ? <p className="text-[13px] text-murekkep-2">Nakit dönüş yok; ortak zarar payı katkısını aşıyor.</p>
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
