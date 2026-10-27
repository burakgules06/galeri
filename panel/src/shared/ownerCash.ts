// Dukkan sahibinin kasadaki (kasa + bankalar) payi bittiyse harcama / cekis sermayedarlarin parasindan cikar: engellemez, uyarir.
import { api } from "../api/galeri";
import { tl } from "./format";

export async function confirmOwnerCash(cents: number): Promise<boolean> {
  const holders = await api.holders().catch(() => []);
  const owner = holders.find((h) => h.owner);
  if (!owner || holders.length < 2 || cents <= owner.cashCents) return true;
  const own = Math.max(0, owner.cashCents);
  return window.confirm(`${owner.name} adına kasada ${tl(own)} var. ${tl(cents - own)} sermayedarların parasından çıkacak. Devam edilsin mi?`);
}
