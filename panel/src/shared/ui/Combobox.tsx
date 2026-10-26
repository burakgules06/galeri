// Aranabilir secici: yazdikca liste suzulur, secim bir listeden yapilir. Istege bagli alt satir (ornegin "Listede yok").
import { useMemo, useState, type KeyboardEvent } from "react";
import { inputClass } from "./Common";

export function Combobox({ items, value, onChange, placeholder, disabled = false, footer, onFooter, ariaLabel, badge }: {
  items: string[];
  value: string;
  onChange: (v: string) => void;
  placeholder: string;
  disabled?: boolean;
  footer?: string;
  onFooter?: () => void;
  ariaLabel?: string;
  /** Secilen degerin yanina kucuk etiket (ornegin "galeri eklemesi") */
  badge?: (v: string) => string | null;
}) {
  const [text, setText] = useState("");
  const [open, setOpen] = useState(false);
  const results = useMemo(() => {
    const t = text.trim().toLocaleLowerCase("tr-TR");
    return (t ? items.filter((i) => i.toLocaleLowerCase("tr-TR").includes(t)) : items).slice(0, 60);
  }, [text, items]);

  if (value) {
    const b = badge?.(value);
    return (
      <div className={`${inputClass} flex items-center justify-between gap-2 ${disabled ? "opacity-60" : ""}`}>
        <span className="truncate font-semibold">{value}{b && <span className="ml-2 text-[11.5px] font-semibold text-murekkep-3">{b}</span>}</span>
        {!disabled && <button type="button" className="text-[14px] text-plaka font-semibold shrink-0" onClick={() => onChange("")}>Değiştir</button>}
      </div>
    );
  }

  const choose = (v: string) => { onChange(v); setText(""); setOpen(false); };
  const keyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key !== "Enter") return;
    e.preventDefault();   // formu gondermesin
    const exact = results.find((i) => i.toLocaleLowerCase("tr-TR") === text.trim().toLocaleLowerCase("tr-TR"));
    if (exact) choose(exact);
    else if (results.length === 1) choose(results[0]);
  };

  return (
    <div className="relative">
      <input className={inputClass} placeholder={placeholder} value={text} disabled={disabled} aria-label={ariaLabel}
        onChange={(e) => { setText(e.target.value); setOpen(true); }} onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)} onKeyDown={keyDown} />
      {open && !disabled && (
        <ul className="absolute z-20 mt-1 w-full bg-yuzey border border-cizgi-2 rounded-lg shadow-lg max-h-64 overflow-auto py-1">
          {results.map((i) => (
            <li key={i}>
              <button type="button" className="w-full text-left px-3 py-2 hover:bg-yuzey-2" onMouseDown={(e) => e.preventDefault()} onClick={() => choose(i)}>
                {i}{badge?.(i) && <span className="ml-2 text-[11.5px] font-semibold text-murekkep-3">{badge(i)}</span>}
              </button>
            </li>
          ))}
          {results.length === 0 && <li className="px-3 py-2 text-[14px] text-murekkep-3">Eşleşen yok</li>}
          {footer && onFooter && (
            <li className="border-t border-cizgi mt-1 pt-1">
              <button type="button" className="w-full text-left px-3 py-2 text-plaka font-semibold hover:bg-plaka-acik"
                onMouseDown={(e) => e.preventDefault()} onClick={() => { setOpen(false); onFooter(); }}>{footer}</button>
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
