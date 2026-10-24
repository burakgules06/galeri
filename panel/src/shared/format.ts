// Gosterim yardimcilari. Tutarlar backend'de kurus (tam sayi); arayuzde TL.
import type { AracDurum, HareketResponse, HareketTur, Parca, ParcaDurum, Vites, Yakit } from "./types";

const tlFmt = new Intl.NumberFormat("tr-TR", { maximumFractionDigits: 2, minimumFractionDigits: 0 });
const sayiFmt = new Intl.NumberFormat("tr-TR");

export function tl(kurus: number | null | undefined): string {
  if (kurus == null) return "—";
  return `${tlFmt.format(kurus / 100)} TL`;
}

export function sayi(n: number): string {
  return sayiFmt.format(n);
}

/** "1.250.000" ya da "1250000,50" -> kurus. Bos ise null, gecersizse NaN. */
export function tlToKurus(girdi: string): number | null {
  const t = girdi.trim();
  if (!t) return null;
  const normal = t.replace(/\s|TL/gi, "").replace(/\./g, "").replace(",", ".");
  const deger = Number(normal);
  return Number.isFinite(deger) && deger >= 0 ? Math.round(deger * 100) : NaN;
}

/** Kurus -> forma yazilacak "1.250.000" metni. */
export function kurusToInput(kurus: number | null | undefined): string {
  return kurus == null ? "" : tlFmt.format(kurus / 100);
}

export function tarih(iso: string | null | undefined): string {
  if (!iso) return "—";
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString("tr-TR", { day: "numeric", month: "long", year: "numeric" });
}

export function kisaTarih(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString("tr-TR", { day: "numeric", month: "short" });
}

export function bugunIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export const YAKIT: Record<Yakit, string> = {
  BENZIN: "Benzin", DIZEL: "Dizel", LPG: "LPG", BENZIN_LPG: "Benzin + LPG", HIBRIT: "Hibrit", ELEKTRIK: "Elektrik",
};

export const VITES: Record<Vites, string> = { MANUEL: "Manuel", OTOMATIK: "Otomatik", YARI_OTOMATIK: "Yarı otomatik" };

export const DURUM: Record<AracDurum, string> = { STOKTA: "Stokta", SATILDI: "Satıldı", KAPANDI: "Kapandı" };

export const PARCA_DURUM: Record<ParcaDurum, string> = {
  ORIJINAL: "Orijinal", LOKAL_BOYALI: "Lokal boyalı", BOYALI: "Boyalı", DEGISEN: "Değişen",
};

export const PARCA: Record<Parca, string> = {
  ON_TAMPON: "Ön tampon", MOTOR_KAPUTU: "Motor kaputu", TAVAN: "Tavan", BAGAJ_KAPAGI: "Bagaj kapağı",
  ARKA_TAMPON: "Arka tampon", SOL_ON_CAMURLUK: "Sol ön çamurluk", SOL_ON_KAPI: "Sol ön kapı",
  SOL_ARKA_KAPI: "Sol arka kapı", SOL_ARKA_CAMURLUK: "Sol arka çamurluk", SAG_ON_CAMURLUK: "Sağ ön çamurluk",
  SAG_ON_KAPI: "Sağ ön kapı", SAG_ARKA_KAPI: "Sağ arka kapı", SAG_ARKA_CAMURLUK: "Sağ arka çamurluk",
};

export const AYLAR = ["Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"];

/** Plaka girerken anlik bicim: "34abc123" -> "34 ABC 123" (eksikse oldugu gibi buyuk harf). */
export function plakaBicimle(girdi: string): string {
  const t = girdi.toLocaleUpperCase("tr-TR").replace(/İ/g, "I").replace(/[^0-9A-Z]/g, "");
  const m = t.match(/^(\d{0,2})([A-Z]{0,3})(\d{0,4})$/);
  if (!m) return girdi.toLocaleUpperCase("tr-TR");
  return [m[1], m[2], m[3]].filter(Boolean).join(" ");
}

export const HAREKET_TUR: Record<HareketTur, string> = {
  ARAC_ALIS: "Araç alışı", ARAC_MASRAF: "Araç masrafı", GENEL_GIDER: "Genel gider", SERMAYE: "Sermaye",
};

/**
 * Hareketin bir hesaba gore yonu: +1 giris, -1 cikis, 0 ilgisiz.
 * hesapId yoksa kasa/banka toplamina gore bakilir (kasadan bankaya aktarim 0 sayilir).
 */
export function hareketYonu(h: HareketResponse, hesapId: string | null): 1 | -1 | 0 {
  if (hesapId) return h.hedef.id === hesapId ? 1 : h.kaynak.id === hesapId ? -1 : 0;
  const para = (t: string) => t === "KASA" || t === "BANKA";
  if (para(h.hedef.tur) && !para(h.kaynak.tur)) return 1;
  if (para(h.kaynak.tur) && !para(h.hedef.tur)) return -1;
  return 0;
}
