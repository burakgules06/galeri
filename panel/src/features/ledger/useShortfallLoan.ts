// Parasi yetmeyen islem (sermaye cekisi / aktarimi 1036, ortak / konsinye alis 1042): sunucu kisiyi ve eksik tutari doner.
// Yonetici ise "Kimden borc alsin?" sorulur; borc verilirse ayni istek tekrar gonderilir. Vazgecilirse ilk hata aynen firlatilir
// (formda gorunur, or. "cepten ekleyin"). Yonetici degilse soru yok, hata aynen doner (borcu sadece yonetici girer).
import { useState } from "react";
import { useAuth } from "../../auth/useAuth";
import { ApiError } from "../../shared/errors/ApiError";

export interface Shortfall { userId: string; name: string; shortfallCents: number }

export interface LoanPrompt {
  shortfall: Shortfall;
  message: string;
  accountId?: string;
  date?: string;
  done: (lent: boolean) => void;
}

const SHORTFALL_CODES = [1036, 1042];
const MAX_ROUNDS = 5;   // ortak aracta birden fazla ortagin parasi yetmeyebilir: her biri icin ayri sorulur

export function shortfallOf(e: unknown): Shortfall | null {
  if (!(e instanceof ApiError) || e.code == null || !SHORTFALL_CODES.includes(e.code)) return null;
  const d = e.data as Partial<Shortfall> | null;
  return d && typeof d.userId === "string" && typeof d.shortfallCents === "number" && d.shortfallCents > 0 ? (d as Shortfall) : null;
}

export function useShortfallLoan() {
  const { isAdmin } = useAuth();
  const [prompt, setPrompt] = useState<LoanPrompt | null>(null);

  const run = async <T>(send: () => Promise<T>, context: { accountId?: string | null; date?: string } = {}): Promise<T> => {
    for (let round = 0; ; round++) {
      try {
        return await send();
      } catch (e) {
        const shortfall = shortfallOf(e);
        if (!shortfall || !isAdmin || round >= MAX_ROUNDS) throw e;
        const lent = await new Promise<boolean>((resolve) => setPrompt({
          shortfall, message: (e as Error).message, accountId: context.accountId ?? undefined, date: context.date, done: resolve,
        }));
        setPrompt(null);
        if (!lent) throw e;
      }
    }
  };

  return { run, prompt };
}
