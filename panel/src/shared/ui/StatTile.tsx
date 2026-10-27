// KPI karesi: ust etiket, buyuk sayi, alt satir. Renk sadece anlam tasiyorsa (alacak yesil, verecek kirmizi).
import type { ReactNode } from "react";

type Tone = "neutral" | "positive" | "negative" | "brand";

const VALUE: Record<Tone, string> = {
  neutral: "text-murekkep",
  positive: "text-basari",
  negative: "text-tehlike",
  brand: "text-plaka",
};

const DOT: Record<Tone, string> = {
  neutral: "bg-murekkep-3",
  positive: "bg-basari-canli",
  negative: "bg-tehlike-canli",
  brand: "bg-seri-1",
};

export function StatTile({ label, value, sub, tone = "neutral", children, valueClassName = "truncate text-[28px] md:text-[32px]" }: {
  label: string; value: string; sub?: ReactNode; tone?: Tone; children?: ReactNode; valueClassName?: string;
}) {
  return (
    <div className="kart p-4 md:p-5 flex flex-col min-w-0">
      <p className="etiket flex items-center gap-1.5"><span className={`w-1.5 h-1.5 rounded-full ${DOT[tone]}`} />{label}</p>
      <p className={`sayi font-bold leading-none tracking-tight mt-2.5 ${valueClassName} ${VALUE[tone]}`}>{value}</p>
      {sub && <div className="text-[13px] text-murekkep-2 mt-1.5">{sub}</div>}
      {children && <div className="mt-3">{children}</div>}
    </div>
  );
}
