// Grafiklerin piksel genisligi: SVG'yi gercek genislikte cizeriz ki yazilar esnemesin.
import { useEffect, useRef, useState } from "react";

export function useWidth<T extends HTMLElement>(fallback = 320) {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(fallback);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => setWidth(Math.max(240, Math.round(e.contentRect.width))));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return { ref, width };
}

/** Eksen icin yuvarlak ust sinir ve adim: 7 -> {max: 8, step: 2}. */
export function niceScale(maxValue: number, ticks = 4) {
  if (maxValue <= 0) return { max: ticks, step: 1 };
  const raw = maxValue / ticks;
  const pow = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * pow).find((s) => s >= raw) ?? 10 * pow;
  const niceStep = step < 1 ? 1 : step;
  return { max: Math.ceil(maxValue / niceStep) * niceStep, step: niceStep };
}
