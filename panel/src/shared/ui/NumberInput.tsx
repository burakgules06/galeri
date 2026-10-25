// Tutar, kilometre ve telefon alanlari: yazarken basamaklarina ayrilir (1.250.000,50 · 64.000 · 0530 111 22 33).
// Deger her zaman bicimli metindir; tutar tlToCents, sayi formatNumber ters cevrimiyle okunur. Imlec yerinde kalir.
import { useLayoutEffect, useRef, type ChangeEvent, type InputHTMLAttributes } from "react";
import { countSignificant, formatTyping, isSignificant, phonePrefixDelta, positionAfter, type NumberKind } from "../numberInput";
import { inputClass } from "./Common";

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "inputMode" | "type"> & {
  value: string;
  onValueChange: (value: string) => void;
  kind?: NumberKind;
};

const INPUT_MODE: Record<NumberKind, "decimal" | "numeric" | "tel"> = { money: "decimal", integer: "numeric", phone: "tel" };

export function NumberInput({ value, onValueChange, kind = "money", className = inputClass, ...rest }: Props) {
  const ref = useRef<HTMLInputElement>(null);
  const caret = useRef<number | null>(null);

  useLayoutEffect(() => {
    const el = ref.current;
    if (caret.current != null && el && document.activeElement === el) el.setSelectionRange(caret.current, caret.current);
    caret.current = null;
  });

  const change = (e: ChangeEvent<HTMLInputElement>) => {
    let raw = e.target.value;
    let pos = e.target.selectionStart ?? raw.length;
    // Sadece ayirici silindiyse (rakamlar ayni): kullanici onundeki rakami silmek istemistir
    if (raw.length < value.length && countSignificant(raw, kind) === countSignificant(value, kind)) {
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

  return <input autoComplete="off" {...rest} ref={ref} className={className} value={value} onChange={change} inputMode={INPUT_MODE[kind]} />;
}
