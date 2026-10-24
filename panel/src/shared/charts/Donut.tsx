// Halka grafik: parcalar arasinda 2px bosluk, ortada baslik + deger. Hover'da diger parcalar soner.
import { useState, type ReactNode } from "react";

export interface Slice { key: string; label: string; value: number; color: string }

function arc(cx: number, cy: number, r: number, a: number) {
  return [cx + r * Math.sin(a), cy - r * Math.cos(a)];
}

export function Donut({ slices, size = 188, thickness = 24, center, ariaLabel, active, onActive }: {
  slices: Slice[];
  size?: number;
  thickness?: number;
  center?: ReactNode;
  ariaLabel: string;
  active?: string | null;
  onActive?: (key: string | null) => void;
}) {
  const [hoverLocal, setHoverLocal] = useState<string | null>(null);
  const hover = active !== undefined ? active : hoverLocal;
  const setHover = onActive ?? setHoverLocal;
  const total = slices.reduce((t, s) => t + s.value, 0);
  const R = size / 2, r = R - thickness, cx = R, cy = R;
  const pad = slices.length > 1 ? 0.012 : 0;   // ~2px aralik
  let a0 = 0;

  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <svg width={size} height={size} role="img" aria-label={ariaLabel}>
        {total === 0 && <circle cx={cx} cy={cy} r={R - thickness / 2} fill="none" stroke="var(--color-izgara)" strokeWidth={thickness} />}
        {total > 0 && slices.length === 1 && (
          <circle cx={cx} cy={cy} r={R - thickness / 2} fill="none" stroke={slices[0].color} strokeWidth={thickness} />
        )}
        {total > 0 && slices.length > 1 && slices.map((s) => {
          const sweep = (s.value / total) * Math.PI * 2;
          const start = a0 + pad, end = a0 + sweep - pad;
          a0 += sweep;
          if (end <= start) return null;
          const large = end - start > Math.PI ? 1 : 0;
          const [x1, y1] = arc(cx, cy, R, start), [x2, y2] = arc(cx, cy, R, end);
          const [x3, y3] = arc(cx, cy, r, end), [x4, y4] = arc(cx, cy, r, start);
          return (
            <path key={s.key} className="grafik-isaret" fill={s.color} opacity={hover && hover !== s.key ? 0.3 : 1}
              d={`M${x1},${y1} A${R},${R} 0 ${large} 1 ${x2},${y2} L${x3},${y3} A${r},${r} 0 ${large} 0 ${x4},${y4} Z`}
              onMouseEnter={() => setHover(s.key)} onMouseLeave={() => setHover(null)}>
              <title>{s.label}</title>
            </path>
          );
        })}
      </svg>
      {center && <div className="absolute inset-0 flex flex-col items-center justify-center text-center pointer-events-none">{center}</div>}
    </div>
  );
}

/** Yuzde yuz yatay serit: parcalar arasinda 2px bosluk, uclar yuvarlak. */
export function SplitBar({ parts, height = 10 }: { parts: { key: string; value: number; color: string }[]; height?: number }) {
  const total = parts.reduce((t, p) => t + Math.max(0, p.value), 0);
  return (
    <div className="flex w-full gap-[2px] overflow-hidden" style={{ height, borderRadius: height / 2 }}>
      {total === 0
        ? <span className="w-full bg-izgara" />
        : parts.filter((p) => p.value > 0).map((p) => (
          <span key={p.key} style={{ flexGrow: p.value, background: p.color }} />
        ))}
    </div>
  );
}
