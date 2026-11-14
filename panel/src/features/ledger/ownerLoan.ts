// Dukkan sahibinin borcu seciminin degeri: hesap id'si ya da POCKET (cebinden). Cebinden secilince para pocketAccountId'ye
// girmis sayilir (cekiste cekilen hesap, alista alis hesabi, yoksa kasa).
import type { HoldingsResponse, OwnerLoan, Shortfall } from "../../shared/types";
import { ApiError } from "../../shared/errors/ApiError";

export const POCKET = "POCKET";

export function ownerLoanOf(choice: string, pocketAccountId: string): OwnerLoan {
  return choice === POCKET ? { accountId: pocketAccountId, pocket: true } : { accountId: choice, pocket: false };
}

/** Dukkan sahibinin hesaptaki payi (holdings'te owner parcalari). */
export function ownerPartIn(holdings: HoldingsResponse | null, accountId: string): number {
  const a = holdings?.accounts.find((x) => x.accountId === accountId);
  return a ? a.parts.filter((p) => p.owner).reduce((t, p) => t + p.cents, 0) : 0;
}

/** 1042 (ortak / konsinye alista nakit yetmiyor) hatasindaki kisiler ve eksik tutarlar. */
export function shortfallsOf(e: unknown): Shortfall[] {
  if (!(e instanceof ApiError) || e.code !== 1042 || !Array.isArray(e.data)) return [];
  return (e.data as Shortfall[]).filter((s) => typeof s?.userId === "string" && s.shortfallCents > 0);
}
