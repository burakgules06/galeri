// Tutar, kilometre ve telefon alanlari: yazarken basamaklarina ayrilir (1.250.000,50 · 64.000 · 0530 111 22 33).
// Deger her zaman bicimli metindir; tutar tlToCents, sayi formatNumber ters cevrimiyle okunur. Imlec yerinde kalir.
// Tutarda altta onizleme ("125.050,00 TL"). Noktadan sonra 1-2 rakam yazilip birakildiysa (Ingilizce kurus aliskanligi,
// "1250.50") uyari: nokta yok sayildigi icin tutar 125.050 olur. 3 rakama tamamlanirsa binlik nokta sayilir, uyari kalkar.
import { useLayoutEffect, useRef, useState, type ChangeEvent, type InputHTMLAttributes } from "react";
import { tlExact, tlToCents } from "../format";
import { countSignificant, formatTyping, isSignificant, phonePrefixDelta, positionAfter, type NumberKind } from "../numberInput";
import { inputClass } from "./Common";

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "inputMode" | "type"> & {
  value: string;
  onValueChange: (value: string) => void;
  kind?: NumberKind;
  /** Tutarda altta "125.050,00 TL" onizlemesi; yuzde gibi tutar olmayan alanlarda kapatilir. */
  preview?: boolean;
};

const INPUT_MODE: Record<NumberKind, "decimal" | "numeric" | "tel"> = { money: "decimal", integer: "numeric", phone: "tel" };

/** raw, value'dan tek bir ayirici (nokta / bosluk) silinerek mi elde edildi. */
function onlySeparatorRemoved(value: string, raw: string, kind: NumberKind): boolean {
  if (raw.length !== value.length - 1) return false;
  for (let i = 0; i < value.length; i++) {
    if (!isSignificant(value[i], kind) && value.slice(0, i) + value.slice(i + 1) === raw) return true;
  }
  return false;
}

export function NumberInput({ value, onValueChange, kind = "money", preview = kind === "money", className = inputClass, ...rest }: Props) {
  const ref = useRef<HTMLInputElement>(null);
  const caret = useRef<number | null>(null);
  // Son yazilan noktadan sonra yazilan rakam sayisi (nokta yazilmadiysa null)
  const [afterDot, setAfterDot] = useState<number | null>(null);

  useLayoutEffect(() => {
    const el = ref.current;
    if (caret.current != null && el && document.activeElement === el) el.setSelectionRange(caret.current, caret.current);
    caret.current = null;
  });

  const trackDot = (raw: string, pos: number) => {
    if (kind !== "money") return;
    const inserted = raw.length - value.length;
    const pasted = /\.(\d{1,2})$/.exec(raw);
    if (inserted > 1 && pasted) setAfterDot(pasted[1].length);                        // yapistirma: "1250.50"
    else if (inserted === 1 && raw[pos - 1] === ".") setAfterDot(0);                   // nokta yazildi
    else if (inserted === 1 && raw[pos - 1] === ",") setAfterDot(null);                // virgul: kurus dogru girildi
    else if (inserted === 1 && /\d/.test(raw[pos - 1] ?? "") && afterDot != null) setAfterDot(afterDot + 1);
    else if (inserted < 0 || !raw) setAfterDot(null);
  };

  const change = (e: ChangeEvent<HTMLInputElement>) => {
    let raw = e.target.value;
    let pos = e.target.selectionStart ?? raw.length;
    trackDot(raw, pos);
    // Sadece tek bir ayirici silindiyse (geri kalan metin ayni): kullanici onundeki rakami silmek istemistir.
    // Tum metin degistirildiyse (secip yapistirma) dokunulmaz.
    if (onlySeparatorRemoved(value, raw, kind)) {
      let i = pos - 1;
      while (i >= 0 && !isSignificant(raw[i], kind)) i--;
      if (i >= 0) {
        raw = raw.slice(0, i) + raw.slice(i + 1);
        pos = i;
      }
    }
    let significantBefore = countSignificant(raw.slice(0, pos), kind);
    if (kind === "phone" && significantBefore > 0) significantBefore = Math.max(0, significantBefore + phonePrefixDelta(raw));
    const formatted = formatTyping(raw, kind);
    caret.current = positionAfter(formatted, significantBefore);
    onValueChange(formatted);
  };

  const input = <input autoComplete="off" {...rest} ref={ref} className={className} value={value} onChange={change} inputMode={INPUT_MODE[kind]} />;
  if (!preview) return input;

  const cents = tlToCents(value);
  const dotWarning = afterDot != null && afterDot >= 1 && afterDot <= 2;
  return (
    <span className="block">
      {input}
      {dotWarning
        ? <span role="alert" className="block text-[12.5px] font-semibold text-uyari mt-1">Kuruş için virgül kullanın: 1.250,50 · şu an {cents != null && !Number.isNaN(cents) ? tlExact(cents) : "—"}</span>
        : cents != null && !Number.isNaN(cents) && <span className="block text-[12.5px] text-murekkep-3 mt-1 sayi">{tlExact(cents)}</span>}
    </span>
  );
}
