// Hasar semasini resim (PNG) olarak uretir: ilan sitesine fotograf olarak eklenir (aciklamaya resim yapistirilamiyor).
// Renkler tema degiskenlerinden okunur.
import { PART_CONDITION } from "../../shared/format";
import type { BodyPart, PartCondition } from "../../shared/types";
import { GLASS, SHAPE } from "./damageShapes";
import { conditionColor, damageSummary, token } from "./listingText";

// Yatay resim: solda ustten arac cizimi, sagda baslik, durum sayilari ve hasarli parcalar (ilan fotografina uygun oran).
const MAP_X = 16;
const MAP_Y = 40;            // cizim viewBox'i 0..400; ustte "On" yazisi icin pay
const PANEL_X = 270;
const WIDTH = 560;
const HEIGHT = 470;
const SCALE = 2;

/** Kelime bolmeden basit satir kirma (yaklasik karakter sayisiyla). */
function wrap(text: string, max: number): string[] {
  const lines: string[] = [];
  let line = "";
  for (const word of text.split(/\s+/)) {
    if (line && (line + " " + word).length > max) {
      lines.push(line);
      line = word;
    } else {
      line = line ? `${line} ${word}` : word;
    }
  }
  if (line) lines.push(line);
  return lines;
}

function svgSource(parts: Record<BodyPart, PartCondition>, title: string): { svg: string; width: number; height: number } {
  const ink = token("--color-murekkep");
  const ink2 = token("--color-murekkep-2");
  const ink3 = token("--color-murekkep-3");
  const line = token("--color-cizgi");
  const glass = token("--color-plaka-acik");
  const white = token("--color-yuzey");
  const font = "Arial, Helvetica, sans-serif";
  const escape = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;");
  const text = (x: number, y: number, value: string, attrs: string) =>
    `<text x="${x}" y="${y}" font-family="${font}" ${attrs}>${escape(value)}</text>`;

  const paths = (Object.keys(SHAPE) as BodyPart[])
    .map((p) => `<path d="${SHAPE[p]}" fill="${conditionColor(parts[p] ?? "ORIGINAL")}" stroke="${white}" stroke-width="2.5" stroke-linejoin="round"/>`)
    .join("");
  const map = `<g transform="translate(${MAP_X} ${MAP_Y})">`
    + text(110, -10, "Ön", `font-size="12" text-anchor="middle" fill="${ink3}"`)
    + `<text x="-4" y="200" font-family="${font}" font-size="12" text-anchor="middle" fill="${ink3}" transform="rotate(-90 -4 200)">Sol</text>`
    + `<text x="226" y="200" font-family="${font}" font-size="12" text-anchor="middle" fill="${ink3}" transform="rotate(90 226 200)">Sağ</text>`
    + GLASS.map((d) => `<path d="${d}" fill="${glass}" stroke="${white}" stroke-width="2"/>`).join("")
    + paths + `</g>`;

  // Sag panel: baslik, arac adi, durum sayilari, hasarli parcalar
  let y = 52;
  let panel = text(PANEL_X, y, "Ekspertiz", `font-size="22" font-weight="bold" fill="${ink}"`);
  for (const l of wrap(title, 30).slice(0, 2)) {
    y += 22;
    panel += text(PANEL_X, y, l, `font-size="15" fill="${ink2}"`);
  }
  y += 18;
  panel += `<line x1="${PANEL_X}" y1="${y}" x2="${WIDTH - 24}" y2="${y}" stroke="${line}" stroke-width="1"/>`;
  const summary = damageSummary(parts);
  for (const s of summary) {
    y += 34;
    panel += `<rect x="${PANEL_X}" y="${y - 15}" width="18" height="18" rx="4" fill="${conditionColor(s.condition)}"/>`
      + text(PANEL_X + 28, y, PART_CONDITION[s.condition], `font-size="17" fill="${ink}"`)
      + text(WIDTH - 24, y, String(s.count), `font-size="18" font-weight="bold" text-anchor="end" fill="${ink}"`);
  }
  const damaged = summary.filter((s) => s.parts.length > 0);
  if (damaged.length > 0) {
    y += 16;
    panel += `<line x1="${PANEL_X}" y1="${y}" x2="${WIDTH - 24}" y2="${y}" stroke="${line}" stroke-width="1"/>`;
    for (const s of damaged) {
      for (const l of wrap(`${PART_CONDITION[s.condition]}: ${s.parts.join(", ")}`, 34)) {
        y += 21;
        if (y > HEIGHT - 12) break;
        panel += text(PANEL_X, y, l, `font-size="14" fill="${ink}"`);
      }
    }
  } else {
    y += 34;
    panel += text(PANEL_X, y, "Boyasız, değişensiz", `font-size="16" font-weight="bold" fill="${ink}"`);
  }

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${WIDTH}" height="${HEIGHT}" viewBox="0 0 ${WIDTH} ${HEIGHT}">`
    + `<rect x="0" y="0" width="${WIDTH}" height="${HEIGHT}" fill="${white}"/>` + map + panel + `</svg>`;
  return { svg, width: WIDTH, height: HEIGHT };
}

/** PNG olarak hasar semasi. */
export async function damageImage(parts: Record<BodyPart, PartCondition>, title: string): Promise<Blob> {
  const { svg, width, height } = svgSource(parts, title);
  const img = new Image();
  img.decoding = "async";
  img.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
  await img.decode();
  const canvas = document.createElement("canvas");
  canvas.width = width * SCALE;
  canvas.height = height * SCALE;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Resim hazırlanamadı");
  ctx.scale(SCALE, SCALE);
  ctx.drawImage(img, 0, 0, width, height);
  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("Resim hazırlanamadı"))), "image/png"));
}
