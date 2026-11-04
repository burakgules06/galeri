// Konsinye araca masraf: sahibinin nakitteki parasi yetmiyorsa sunucu 1061 doner. Kullaniciya sunucunun mesajiyla sorulur
// ("eksiye dusecek, devam?"); onaylanirsa istek confirmNegative ile tekrar gonderilir. Vazgecilirse null.
import { ApiError } from "./errors/ApiError";

export const CONSIGNOR_CASH_NEGATIVE = 1061;

export async function withNegativeConfirm<T>(send: (confirmNegative: boolean) => Promise<T>): Promise<T | null> {
  try {
    return await send(false);
  } catch (e) {
    if (!(e instanceof ApiError) || e.code !== CONSIGNOR_CASH_NEGATIVE) throw e;
    if (!window.confirm(e.message)) return null;
    return send(true);
  }
}
