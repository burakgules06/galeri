// Ortak arayuz parcalari. Renkler index.css @theme'den gelir; burada sabit renk kodu yok.
import { useEffect, type ButtonHTMLAttributes, type ReactNode } from "react";

type ButtonVariant = "primary" | "secondary" | "quiet" | "danger";

const BUTTON: Record<ButtonVariant, string> = {
  primary: "bg-plaka text-white hover:bg-plaka-koyu",
  secondary: "bg-yuzey text-murekkep border border-cizgi-2 hover:bg-yuzey-2",
  quiet: "text-murekkep-2 hover:bg-yuzey-2",
  danger: "bg-yuzey text-tehlike border border-cizgi-2 hover:bg-tehlike-zemin",
};

export function Button({ variant = "primary", className = "", ...p }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant }) {
  return (
    <button
      {...p}
      className={`basilir h-10 px-4 rounded-lg font-semibold text-[15px] inline-flex items-center justify-center gap-2 disabled:opacity-50 disabled:pointer-events-none ${BUTTON[variant]} ${className}`}
    />
  );
}

export function Field({ label, hint, error, children, className = "" }: {
  label: string; hint?: string; error?: string | null; children: ReactNode; className?: string;
}) {
  return (
    <label className={`block ${className}`}>
      <span className="block text-[13px] font-semibold text-murekkep-2 mb-1">{label}</span>
      {children}
      {error ? <span className="block text-[13px] text-tehlike mt-1">{error}</span>
        : hint ? <span className="block text-[13px] text-murekkep-3 mt-1">{hint}</span> : null}
    </label>
  );
}

export const inputClass =
  "w-full h-10 rounded-lg border border-cizgi-2 bg-yuzey px-3 text-[15px] placeholder:text-murekkep-3 focus:outline-none focus:border-plaka focus:ring-2 focus:ring-plaka/15";

export function Segment<T extends string>({ value, options, onChange }: {
  value: T; options: { value: T; label: string }[]; onChange: (v: T) => void;
}) {
  return (
    <div className="flex flex-wrap gap-1.5" role="radiogroup">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={value === o.value}
          onClick={() => onChange(o.value)}
          className={`basilir h-9 px-3 rounded-lg text-[14px] font-semibold border ${
            value === o.value ? "bg-murekkep text-white border-murekkep" : "bg-yuzey text-murekkep-2 border-cizgi-2 hover:bg-yuzey-2"
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

/** Telefonda alttan acilan tam ekran sayfa, genis ekranda ortada pencere. */
export function Modal({ title, open, onClose, children, footer, wide = false, size }: {
  title: string; open: boolean; onClose: () => void; children: ReactNode; footer?: ReactNode; wide?: boolean;
  size?: "xl";   // genis iki sutunlu formlar (satis, alis)
}) {
  useEffect(() => {
    if (!open) return;
    const onEsc = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onEsc);
    return () => window.removeEventListener("keydown", onEsc);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end md:items-center justify-center">
      <div className="absolute inset-0 bg-murekkep/40" onClick={onClose} />
      <div role="dialog" aria-modal="true" aria-label={title}
        className={`relative bg-yuzey w-full ${size === "xl" ? "md:max-w-5xl" : wide ? "md:max-w-3xl" : "md:max-w-xl"} max-h-[100dvh] md:max-h-[88vh] md:rounded-2xl rounded-t-2xl flex flex-col shadow-xl`}>
        <div className="flex items-center justify-between px-5 h-14 border-b border-cizgi shrink-0">
          <h2 className="font-cond font-bold text-[22px] tracking-tight">{title}</h2>
          <button onClick={onClose} aria-label="Kapat" className="basilir w-9 h-9 rounded-lg hover:bg-yuzey-2 grid place-items-center text-murekkep-2">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
          </button>
        </div>
        <div className="overflow-y-auto px-5 py-4 grow">{children}</div>
        {footer && <div className="px-5 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] border-t border-cizgi flex justify-end gap-2 shrink-0 bg-yuzey sticky bottom-0">{footer}</div>}
      </div>
    </div>
  );
}

export function Empty({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="text-center py-14 px-6">
      <p className="font-cond font-semibold text-[22px]">{title}</p>
      {description && <p className="text-murekkep-2 mt-1 max-w-sm mx-auto">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorBox({ message }: { message: string | null }) {
  if (!message) return null;
  return <p role="alert" className="rounded-lg bg-tehlike-zemin text-tehlike text-[14px] px-3 py-2">{message}</p>;
}
