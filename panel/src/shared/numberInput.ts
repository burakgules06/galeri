// Yazarken sayi bicimlendirme (NumberInput kullanir). Turkce yazim: binlik nokta, kurus virgul (1.250.000,50).
// Nokta yazilirsa yok sayilir: "1250.50" gibi Ingilizce yazim 125.050'ye donusmesin, kullanici ekranda farki gorsun.

export type NumberKind = "money" | "integer" | "phone";

const groupThousands = (digits: string) => digits.replace(/\B(?=(\d{3})+(?!\d))/g, ".");

/** Anlamli karakter: rakam; tutarda kurus virgulu de. Imlec konumu bunlarin sayisiyla korunur. */
export function isSignificant(ch: string, kind: NumberKind): boolean {
  return (ch >= "0" && ch <= "9") || (kind === "money" && ch === ",");
}

export function countSignificant(text: string, kind: NumberKind): number {
  let n = 0;
  for (const ch of text) if (isSignificant(ch, kind)) n++;
  return n;
}

/** Bicimli metinde n anlamli karakterden hemen sonraki konum. */
export function positionAfter(text: string, n: number): number {
  if (n <= 0) return 0;
  let seen = 0;
  for (let i = 0; i < text.length; i++) {
    if ((text[i] >= "0" && text[i] <= "9") || text[i] === ",") seen++;
    if (seen === n) return i + 1;
  }
  return text.length;
}

/** Ham girisi bicimler. money: 1.250.000,50 · integer: 64.000 · phone: 0530 111 22 33 */
export function formatTyping(raw: string, kind: NumberKind): string {
  if (kind === "phone") return formatPhoneTyping(raw);

  let s = "";
  let comma = false;
  for (const ch of raw) {
    if (ch >= "0" && ch <= "9") s += ch;
    else if (ch === "," && kind === "money" && !comma) { s += ","; comma = true; }
  }
  const [rawInt, frac] = s.split(",");
  const int = rawInt.replace(/^0+(?=\d)/, "");   // bastaki sifirlar: "007" -> "7"
  if (frac === undefined) return groupThousands(int);
  return `${groupThousands(int || "0")},${frac.slice(0, 2)}`;
}

/**
 * Telefonda basa eklenen ya da cikarilan rakam sayisi ("5..." -> "05...": +1, "905..." -> "05...": -1).
 * Imlec hesabi bunu ekler; yoksa imlec bir karakter geride kalir.
 */
export function phonePrefixDelta(raw: string): number {
  const d = raw.replace(/\D/g, "");
  if (d.startsWith("90") && d.length > 2 && d[2] === "5") return -1;
  return d.startsWith("5") ? 1 : 0;
}

/** Cep telefonu: "5301112233", "+90 530..." ya da "0530..." -> "0530 111 22 33". Baska bicimler rakam olarak kalir. */
function formatPhoneTyping(raw: string): string {
  let d = raw.replace(/\D/g, "");
  if (d.startsWith("90") && d.length > 2 && d[2] === "5") d = "0" + d.slice(2);
  else if (d.startsWith("5")) d = "0" + d;
  if (!d.startsWith("05")) return d.slice(0, 15);
  d = d.slice(0, 11);
  const parts = [d.slice(0, 4), d.slice(4, 7), d.slice(7, 9), d.slice(9, 11)].filter(Boolean);
  return parts.join(" ");
}
