// Gosterim yardimcilari. Tutarlar backend'de kurus (tam sayi); arayuzde TL.
import type { BodyPart, Fuel, LedgerEntryResponse, LedgerEntryType, PartCondition, Transmission, VehicleStatus } from "./types";

const tlFormat = new Intl.NumberFormat("tr-TR", { maximumFractionDigits: 2, minimumFractionDigits: 0 });
const numberFormat = new Intl.NumberFormat("tr-TR");

export function tl(cents: number | null | undefined): string {
  if (cents == null) return "—";
  return `${tlFormat.format(cents / 100)} TL`;
}

export function formatNumber(n: number): string {
  return numberFormat.format(n);
}

/** "1.250.000" ya da "1250000,50" -> kurus. Bos ise null, gecersizse NaN. */
export function tlToCents(input: string): number | null {
  const t = input.trim();
  if (!t) return null;
  const normalized = t.replace(/\s|TL/gi, "").replace(/\./g, "").replace(",", ".");
  const value = Number(normalized);
  return Number.isFinite(value) && value >= 0 ? Math.round(value * 100) : NaN;
}

/** Kurus -> forma yazilacak "1.250.000" metni. */
export function centsToInput(cents: number | null | undefined): string {
  return cents == null ? "" : tlFormat.format(cents / 100);
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString("tr-TR", { day: "numeric", month: "long", year: "numeric" });
}

export function shortDate(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString("tr-TR", { day: "numeric", month: "short" });
}

export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export const FUEL: Record<Fuel, string> = {
  GASOLINE: "Benzin", DIESEL: "Dizel", LPG: "LPG", GASOLINE_LPG: "Benzin + LPG", HYBRID: "Hibrit", ELECTRIC: "Elektrik",
};

export const TRANSMISSION: Record<Transmission, string> = { MANUAL: "Manuel", AUTOMATIC: "Otomatik", SEMI_AUTOMATIC: "Yarı otomatik" };

export const STATUS: Record<VehicleStatus, string> = { IN_STOCK: "Stokta", SOLD: "Satıldı", CLOSED: "Kapandı" };

export const PART_CONDITION: Record<PartCondition, string> = {
  ORIGINAL: "Orijinal", LOCALLY_PAINTED: "Lokal boyalı", PAINTED: "Boyalı", REPLACED: "Değişen",
};

export const BODY_PART: Record<BodyPart, string> = {
  FRONT_BUMPER: "Ön tampon", HOOD: "Motor kaputu", ROOF: "Tavan", TRUNK_LID: "Bagaj kapağı",
  REAR_BUMPER: "Arka tampon", LEFT_FRONT_FENDER: "Sol ön çamurluk", LEFT_FRONT_DOOR: "Sol ön kapı",
  LEFT_REAR_DOOR: "Sol arka kapı", LEFT_REAR_FENDER: "Sol arka çamurluk", RIGHT_FRONT_FENDER: "Sağ ön çamurluk",
  RIGHT_FRONT_DOOR: "Sağ ön kapı", RIGHT_REAR_DOOR: "Sağ arka kapı", RIGHT_REAR_FENDER: "Sağ arka çamurluk",
};

export const MONTHS = ["Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"];

/** Plaka girerken anlik bicim: "34abc123" -> "34 ABC 123" (eksikse oldugu gibi buyuk harf). */
export function formatPlate(input: string): string {
  const t = input.toLocaleUpperCase("tr-TR").replace(/İ/g, "I").replace(/[^0-9A-Z]/g, "");
  const m = t.match(/^(\d{0,2})([A-Z]{0,3})(\d{0,4})$/);
  if (!m) return input.toLocaleUpperCase("tr-TR");
  return [m[1], m[2], m[3]].filter(Boolean).join(" ");
}

export const ENTRY_TYPE: Record<LedgerEntryType, string> = {
  VEHICLE_PURCHASE: "Araç alışı", VEHICLE_EXPENSE: "Araç masrafı", GENERAL_EXPENSE: "Genel gider", CAPITAL: "Sermaye",
  VEHICLE_SALE: "Araç satışı", COLLECTION: "Tahsilat", PAYMENT: "Ödeme",
};

/** Buyuk tutarlar icin kisa yazim: 1.250.000 TL -> "1,25 Mn ₺", 395.000 -> "395 B ₺". */
export function tlShort(cents: number): string {
  const v = cents / 100;
  const abs = Math.abs(v);
  if (abs >= 1_000_000) return `${new Intl.NumberFormat("tr-TR", { maximumFractionDigits: 2 }).format(v / 1_000_000)} Mn ₺`;
  if (abs >= 1_000) return `${new Intl.NumberFormat("tr-TR", { maximumFractionDigits: 0 }).format(v / 1_000)} B ₺`;
  return `${tlFormat.format(v)} ₺`;
}

/** Baz puan -> "%68,4" */
export function percent(basisPoints: number, digits = 1): string {
  return `%${new Intl.NumberFormat("tr-TR", { maximumFractionDigits: digits, minimumFractionDigits: 0 }).format(basisPoints / 100)}`;
}

/** Telefon: 905301112233 -> "0530 111 22 33" */
export function formatPhone(phone: string | null | undefined): string {
  if (!phone) return "";
  const d = phone.replace(/\D/g, "").replace(/^90/, "");
  return d.length === 10 ? `0${d.slice(0, 3)} ${d.slice(3, 6)} ${d.slice(6, 8)} ${d.slice(8)}` : phone;
}

/** Grafik serileri: sabit sira, tekrar yok (index.css --color-seri-*). */
export const SERIES = ["var(--color-seri-1)", "var(--color-seri-2)", "var(--color-seri-3)", "var(--color-seri-4)", "var(--color-seri-5)", "var(--color-seri-6)"];

export const SHORT_MONTHS = ["Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"];

/**
 * Hareketin bir hesaba gore yonu: +1 giris, -1 cikis, 0 ilgisiz.
 * accountId yoksa kasa/banka toplamina gore bakilir (kasadan bankaya aktarim 0 sayilir).
 */
export function entryDirection(e: LedgerEntryResponse, accountId: string | null): 1 | -1 | 0 {
  if (accountId) return e.target.id === accountId ? 1 : e.source.id === accountId ? -1 : 0;
  const isMoney = (t: string) => t === "CASH" || t === "BANK";
  if (isMoney(e.target.type) && !isMoney(e.source.type)) return 1;
  if (isMoney(e.source.type) && !isMoney(e.target.type)) return -1;
  return 0;
}
