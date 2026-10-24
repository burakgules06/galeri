// Gruplu cubuk grafik: solda deger ekseni, altta kategoriler (aylar), istenirse cubuklarin ustunde etiket (yuzde).
// Tek eksen; seriler sabit renkte. Hover / dokunma ipucu gosterir, tiklama grubu secer.
import { useState } from "react";
import { niceScale, useWidth } from "./useWidth";

export interface BarSeries { name: string; color: string; values: number[] }

const PAD = { left: 34, right: 6, top: 28, bottom: 28 };
const GAP = 2;

export function BarChart({ categories, series, topLabels, height = 260, selected = null, onSelect, format = (n) => String(n), ariaLabel, minMax = 4 }: {
  categories: string[];
  /** Eksenin en az ulasacagi deger: 1 aracli ay cubugu tavana vurmasin. */
  minMax?: number;
  series: BarSeries[];
  topLabels?: string[];
  height?: number;
  selected?: number | null;
  onSelect?: (index: number) => void;
  format?: (n: number) => string;
  ariaLabel: string;
}) {
  const { ref, width } = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const max = Math.max(0, ...series.flatMap((s) => s.values));
  const scale = niceScale(Math.max(max, minMax));
  const plotW = width - PAD.left - PAD.right;
  const plotH = height - PAD.top - PAD.bottom;
  const groupW = plotW / categories.length;
  const barW = Math.max(4, Math.min(22, (groupW * 0.62 - GAP * (series.length - 1)) / series.length));
  const groupInner = barW * series.length + GAP * (series.length - 1);
  const y = (v: number) => PAD.top + plotH - (v / scale.max) * plotH;
  const ticks = Array.from({ length: Math.round(scale.max / scale.step) + 1 }, (_, i) => i * scale.step);

  return (
    <div ref={ref} className="relative select-none w-full min-w-0 overflow-hidden">
      <svg width={width} height={height} role="img" aria-label={ariaLabel} className="block max-w-none">
        {ticks.map((t) => (
          <g key={t}>
            <line x1={PAD.left} x2={width - PAD.right} y1={y(t)} y2={y(t)} stroke={t === 0 ? "var(--color-eksen)" : "var(--color-izgara)"} strokeWidth="1" />
            <text x={PAD.left - 8} y={y(t)} dy="0.32em" textAnchor="end" className="sayi" fontSize="11.5" fill="var(--color-murekkep-3)">{format(t)}</text>
          </g>
        ))}

        {categories.map((c, i) => {
          const gx = PAD.left + i * groupW;
          const bx = gx + (groupW - groupInner) / 2;
          const isSel = selected === i;
          const dim = hover != null && hover !== i;
          return (
            <g key={c} onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}
              onClick={() => onSelect?.(i)} className={onSelect ? "cursor-pointer" : undefined}>
              {/* genis tiklama alani + secili ay zemini */}
              <rect x={gx + 1} y={PAD.top - 22} width={groupW - 2} height={plotH + 22} rx="8"
                fill={isSel ? "var(--color-plaka-acik)" : "transparent"} />
              {series.map((s, k) => {
                const v = s.values[i] ?? 0;
                const h = Math.max(v > 0 ? 3 : 0, (v / scale.max) * plotH);
                const x = bx + k * (barW + GAP);
                const top = PAD.top + plotH - h;
                const r = Math.min(4, barW / 2, h);
                return h > 0 && (
                  <path key={s.name} className="grafik-isaret" opacity={dim ? 0.35 : 1} fill={s.color}
                    d={`M${x},${top + h} V${top + r} Q${x},${top} ${x + r},${top} H${x + barW - r} Q${x + barW},${top} ${x + barW},${top + r} V${top + h} Z`} />
                );
              })}
              {topLabels?.[i] && (
                <text x={gx + groupW / 2} y={PAD.top - 9} textAnchor="middle" fontSize="11" fontWeight="600"
                  fill={isSel ? "var(--color-plaka-koyu)" : "var(--color-murekkep-2)"}>{topLabels[i]}</text>
              )}
              <text x={gx + groupW / 2} y={height - 9} textAnchor="middle" fontSize="12" fontWeight={isSel ? 700 : 500}
                fill={isSel ? "var(--color-murekkep)" : "var(--color-murekkep-2)"}>{c}</text>
            </g>
          );
        })}
      </svg>

      {hover != null && (
        <div className="pointer-events-none absolute z-10 -translate-x-1/2 rounded-lg bg-murekkep text-white px-3 py-2 text-[13px] shadow-lg whitespace-nowrap"
          style={{ left: Math.min(width - 70, Math.max(70, PAD.left + hover * groupW + groupW / 2)), top: 0 }}>
          <p className="font-semibold mb-1">{categories[hover]}</p>
          {series.map((s) => (
            <p key={s.name} className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-[3px]" style={{ background: s.color }} />
              <span className="text-white/75">{s.name}</span>
              <span className="sayi font-semibold text-[15px] ml-auto pl-3">{format(s.values[hover] ?? 0)}</span>
            </p>
          ))}
        </div>
      )}
    </div>
  );
}

export function Legend({ items }: { items: { label: string; color: string; value?: string }[] }) {
  return (
    <div className="flex flex-wrap gap-x-4 gap-y-1.5">
      {items.map((i) => (
        <span key={i.label} className="inline-flex items-center gap-1.5 text-[13px] text-murekkep-2">
          <span className="w-2.5 h-2.5 rounded-[3px]" style={{ background: i.color }} />
          {i.label}
          {i.value && <span className="sayi font-semibold text-murekkep text-[15px]">{i.value}</span>}
        </span>
      ))}
    </div>
  );
}
