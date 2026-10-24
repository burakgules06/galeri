// Ortak arayuz parcalari. Renkler index.css @theme'den gelir; burada sabit renk kodu yok.
import { useEffect, type ButtonHTMLAttributes, type ReactNode } from "react";

type ButonTur = "ana" | "ikincil" | "sessiz" | "tehlike";

const BUTON: Record<ButonTur, string> = {
  ana: "bg-plaka text-white hover:bg-plaka-koyu",
  ikincil: "bg-yuzey text-murekkep border border-cizgi-2 hover:bg-yuzey-2",
  sessiz: "text-murekkep-2 hover:bg-yuzey-2",
  tehlike: "bg-yuzey text-tehlike border border-cizgi-2 hover:bg-tehlike-zemin",
};

export function Buton({ tur = "ana", className = "", ...p }: ButtonHTMLAttributes<HTMLButtonElement> & { tur?: ButonTur }) {
  return (
    <button
      {...p}
      className={`basilir h-10 px-4 rounded-lg font-semibold text-[15px] inline-flex items-center justify-center gap-2 disabled:opacity-50 disabled:pointer-events-none ${BUTON[tur]} ${className}`}
    />
  );
}

export function Alan({ etiket, ipucu, hata, children, className = "" }: {
  etiket: string; ipucu?: string; hata?: string | null; children: ReactNode; className?: string;
}) {
  return (
    <label className={`block ${className}`}>
      <span className="block text-[13px] font-semibold text-murekkep-2 mb-1">{etiket}</span>
      {children}
      {hata ? <span className="block text-[13px] text-tehlike mt-1">{hata}</span>
        : ipucu ? <span className="block text-[13px] text-murekkep-3 mt-1">{ipucu}</span> : null}
    </label>
  );
}

export const girdiSinifi =
  "w-full h-10 rounded-lg border border-cizgi-2 bg-yuzey px-3 text-[15px] placeholder:text-murekkep-3 focus:outline-none focus:border-plaka focus:ring-2 focus:ring-plaka/15";

export function Segment<T extends string>({ deger, secenekler, onChange }: {
  deger: T; secenekler: { deger: T; etiket: string }[]; onChange: (d: T) => void;
}) {
  return (
    <div className="flex flex-wrap gap-1.5" role="radiogroup">
      {secenekler.map((s) => (
        <button
          key={s.deger}
          type="button"
          role="radio"
          aria-checked={deger === s.deger}
          onClick={() => onChange(s.deger)}
          className={`basilir h-9 px-3 rounded-lg text-[14px] font-semibold border ${
            deger === s.deger ? "bg-murekkep text-white border-murekkep" : "bg-yuzey text-murekkep-2 border-cizgi-2 hover:bg-yuzey-2"
          }`}
        >
          {s.etiket}
        </button>
      ))}
    </div>
  );
}

/** Telefonda alttan acilan tam ekran sayfa, genis ekranda ortada pencere. */
export function Pencere({ baslik, acik, kapat, children, alt }: {
  baslik: string; acik: boolean; kapat: () => void; children: ReactNode; alt?: ReactNode;
}) {
  useEffect(() => {
    if (!acik) return;
    const esc = (e: KeyboardEvent) => e.key === "Escape" && kapat();
    window.addEventListener("keydown", esc);
    return () => window.removeEventListener("keydown", esc);
  }, [acik, kapat]);
  if (!acik) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end md:items-center justify-center">
      <div className="absolute inset-0 bg-murekkep/40" onClick={kapat} />
      <div role="dialog" aria-modal="true" aria-label={baslik}
        className="relative bg-yuzey w-full md:max-w-xl max-h-[100dvh] md:max-h-[88vh] md:rounded-2xl rounded-t-2xl flex flex-col shadow-xl">
        <div className="flex items-center justify-between px-5 h-14 border-b border-cizgi shrink-0">
          <h2 className="font-cond font-bold text-[22px] tracking-tight">{baslik}</h2>
          <button onClick={kapat} aria-label="Kapat" className="basilir w-9 h-9 rounded-lg hover:bg-yuzey-2 grid place-items-center text-murekkep-2">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
          </button>
        </div>
        <div className="overflow-y-auto px-5 py-4 grow">{children}</div>
        {alt && <div className="px-5 py-3 border-t border-cizgi flex justify-end gap-2 shrink-0">{alt}</div>}
      </div>
    </div>
  );
}

export function Bos({ baslik, aciklama, eylem }: { baslik: string; aciklama?: string; eylem?: ReactNode }) {
  return (
    <div className="text-center py-14 px-6">
      <p className="font-cond font-semibold text-[22px]">{baslik}</p>
      {aciklama && <p className="text-murekkep-2 mt-1 max-w-sm mx-auto">{aciklama}</p>}
      {eylem && <div className="mt-4">{eylem}</div>}
    </div>
  );
}

export function HataKutusu({ mesaj }: { mesaj: string | null }) {
  if (!mesaj) return null;
  return <p role="alert" className="rounded-lg bg-tehlike-zemin text-tehlike text-[14px] px-3 py-2">{mesaj}</p>;
}
