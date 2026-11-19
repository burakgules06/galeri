// Sahibinden (ve benzeri ilan siteleri) icin hazir ilan aciklamasi: bizdeki arac bilgilerinden kurumsal galeri ilanlarindaki
// gibi duzenli bir aciklama uretir. Iki bicim birlikte kopyalanir: zengin metin (HTML; aciklama editoru bicimi destekliyorsa
// tablolar ve renkler korunur) ve duz metin (desteklemiyorsa ya da telefondan yapistirilirsa bu kullanilir).
// Ilan sitesi bizim CSS'imizi gormez: renkler tema degiskenlerinden (index.css @theme) okunup satir ici yazilir.
import { BODY_PART, formatPhone, FUEL, PART_CONDITION, TRANSMISSION, tl } from "../../shared/format";
import type { BodyPart, PartCondition, VehicleDetail } from "../../shared/types";
import { SHAPE } from "./damageShapes";

export interface ListingTextInput {
  vehicle: VehicleDetail;
  galleryName: string;
  phone: string | null;
  address: string | null;     // dukkan adresi (vitrin ayarlari); bos ise yazilmaz
  logoUrl: string | null;     // galeri logosu, tam adres (https://...); ilan sitesi resmi kabul ederse en ustte banner olur
  logoSize?: { width: number; height: number } | null;   // logonun gercek boyutu: banner'a orani bozulmadan sigdirmak icin
  description: string;        // vitrindeki ilan aciklamasi (kaydedilmemis hali de olabilir)
  showPlate: boolean;
  showPhone: boolean;
}

const CONDITIONS: PartCondition[] = ["ORIGINAL", "LOCALLY_PAINTED", "PAINTED", "REPLACED"];
const CONDITION_TOKEN: Record<PartCondition, string> = {
  ORIGINAL: "--color-orijinal", LOCALLY_PAINTED: "--color-lokal", PAINTED: "--color-boyali", REPLACED: "--color-degisen",
};

/** Tema rengi (index.css @theme); bulunamazsa bos doner ve stil yazilmaz. */
export function token(name: string): string {
  if (typeof document === "undefined") return "";
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

export function conditionColor(c: PartCondition): string {
  return token(CONDITION_TOKEN[c]);
}

function esc(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function km(n: number): string {
  return `${n.toLocaleString("tr-TR")} km`;
}

function tramerText(cents: number | null): string | null {
  if (cents == null) return null;
  return cents === 0 ? "Hasar kaydı yoktur" : `${tl(cents)} hasar kaydı`;
}

/** Durumlara gore parca sayilari ve hasarli parcalar. */
export function damageSummary(parts: Record<BodyPart, PartCondition>) {
  const all = Object.keys(SHAPE) as BodyPart[];
  return CONDITIONS.map((c) => {
    const list = all.filter((p) => (parts[p] ?? "ORIGINAL") === c);
    return { condition: c, count: list.length, parts: c === "ORIGINAL" ? [] : list.map((p) => BODY_PART[p]) };
  });
}

function specs(i: ListingTextInput): [string, string][] {
  const v = i.vehicle;
  const rows: [string, string][] = [
    ["Model yılı", String(v.modelYear)],
    ["Kilometre", km(v.mileageKm)],
    ["Yakıt", FUEL[v.fuel]],
    ["Vites", TRANSMISSION[v.transmission]],
  ];
  if (v.color) rows.push(["Renk", v.color]);
  if (i.showPlate && v.plate) rows.push(["Plaka", v.plate]);
  return rows;
}

// ------------------------------------------------------------------ zengin metin (HTML)

// Banner'daki logo: kartin tam genisligine kadar buyutulur (kenardan kenara, orani korunarak). Kare / dik logolar dev
// olmasin diye yukseklik en fazla BANNER_MAX_H; o zaman ortada durur. Dar ekranda max-width ile oranini koruyarak kuculur.
const BANNER_W = 760;        // aciklamanin genisligi (max-width)
const BANNER_MAX_H = 220;

function logoImg(i: ListingTextInput): string {
  const src = esc(i.logoUrl ?? "");
  const alt = esc(i.galleryName);
  const size = i.logoSize;
  if (!size || size.width <= 0 || size.height <= 0) {
    return `<img src="${src}" alt="${alt}" style="display:block;margin:0 auto;width:100%;max-width:100%;height:auto;max-height:${BANNER_MAX_H}px;object-fit:contain;">`;
  }
  const aspect = size.width / size.height;
  if (BANNER_W / aspect <= BANNER_MAX_H) {
    // Genis logo: kartin tamami
    return `<img src="${src}" alt="${alt}" width="${BANNER_W}" height="${Math.round(BANNER_W / aspect)}" style="display:block;width:100%;height:auto;">`;
  }
  const w = Math.round(BANNER_MAX_H * aspect);
  return `<img src="${src}" alt="${alt}" width="${w}" height="${BANNER_MAX_H}" style="display:block;margin:0 auto;width:${w}px;max-width:100%;height:auto;">`;
}

export function listingHtml(i: ListingTextInput): string {
  const v = i.vehicle;
  const ink = token("--color-murekkep");
  const ink2 = token("--color-murekkep-2");
  const line = token("--color-cizgi");
  const brand = token("--color-plaka");
  const brandLight = token("--color-plaka-acik");
  const soft = token("--color-zemin");
  const white = token("--color-yuzey");
  const font = "Arial, Helvetica, sans-serif";

  const box = (title: string, body: string) => `
<table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;border:1px solid ${line};margin:0 0 14px 0;">
  <tr><td style="padding:14px 16px;font-family:${font};">
    <div style="font-size:16px;font-weight:bold;color:${ink};margin:0 0 10px 0;">${esc(title)}</div>
    ${body}
  </td></tr>
</table>`;

  // Ust serit: galeri adi, arac, kisa ozet
  // En ustte galeri logosu: kartin genisligine kadar buyutulmus banner (logo yoksa yok)
  const banner = i.logoUrl ? `
<table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;background:${white};margin:0 0 10px 0;">
  <tr><td align="center" valign="middle" style="padding:0;">
    ${logoImg(i)}
  </td></tr>
</table>` : "";

  // Ust satir (simsiyah zemin, beyaz yazi): solda marka ve arac, sagda galeri karti (ad, adres, telefon)
  const dark = ink;
  const light = token("--color-cizgi-2");
  const phoneLine = i.showPhone && i.phone ? formatPhone(i.phone) : null;
  const galleryCard = `
    <div style="font-size:12px;font-weight:bold;letter-spacing:.5px;color:${light};">GALERİ</div>
    <div style="font-size:17px;font-weight:bold;color:${white};margin:3px 0 0 0;">${esc(i.galleryName)}</div>
    ${i.address ? `<div style="font-size:14px;line-height:1.4;color:${light};margin:6px 0 0 0;">${esc(i.address)}</div>` : ""}
    ${phoneLine ? `<div style="font-size:16px;font-weight:bold;color:${white};margin:8px 0 0 0;">${esc(phoneLine)}</div>` : ""}`;
  const header = `${banner}
<table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin:0 0 14px 0;">
  <tr>
    <td width="62%" valign="middle" style="background:${dark};padding:18px 20px;font-family:${font};color:${white};">
      <div style="font-size:30px;font-weight:bold;letter-spacing:.5px;color:${white};">${esc(v.make)}</div>
      <div style="font-size:19px;font-weight:bold;margin:2px 0 6px 0;color:${white};">${esc(v.model)}</div>
      <div style="font-size:15px;color:${light};">${esc([String(v.modelYear), km(v.mileageKm), FUEL[v.fuel], TRANSMISSION[v.transmission]].join(" · "))}</div>
    </td>
    <td width="2%"></td>
    <td width="36%" valign="middle" style="background:${dark};padding:16px 18px;font-family:${font};">${galleryCard}
    </td>
  </tr>
</table>`;

  // Bilgi kartlari: koyu baslik + acik deger, satirda 4 tane (Borusan / kurumsal ilan duzeni)
  const cells = specs(i);
  const rows: string[] = [];
  for (let r = 0; r < cells.length; r += 4) {
    const chunk = cells.slice(r, r + 4);
    while (chunk.length < 4) chunk.push(["", ""]);
    const cell = ([label, value]: [string, string], head: boolean) => label === ""
      ? `<td width="25%" style="padding:0 3px;"></td>`
      : head
        ? `<td width="25%" align="center" style="padding:7px 4px;background:${ink};color:${white};font-family:${font};font-size:12px;font-weight:bold;letter-spacing:.5px;border-left:3px solid ${white};border-right:3px solid ${white};">${esc(label.toLocaleUpperCase("tr-TR"))}</td>`
        : `<td width="25%" align="center" style="padding:11px 4px;background:${soft};color:${ink};font-family:${font};font-size:16px;font-weight:bold;border-left:3px solid ${white};border-right:3px solid ${white};border-bottom:6px solid ${white};">${esc(value)}</td>`;
    rows.push(`<tr>${chunk.map((c) => cell(c, true)).join("")}</tr><tr>${chunk.map((c) => cell(c, false)).join("")}</tr>`);
  }
  const grid = `<table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin:0 0 14px 0;">${rows.join("")}</table>`;

  // Ekspertiz: durum sayilari (renkli kare) ve hasarli parcalar
  const summary = damageSummary(v.parts);
  const damageRows = summary.map((s, k) => `
    <tr style="background:${k % 2 ? soft : white};">
      <td style="padding:8px 10px;font-family:${font};font-size:15px;color:${ink};"><span style="color:${conditionColor(s.condition)};font-size:16px;">■</span>&nbsp;${esc(PART_CONDITION[s.condition])}</td>
      <td align="right" style="padding:8px 10px;font-family:${font};font-size:15px;font-weight:bold;color:${ink};">${s.count}</td>
    </tr>`).join("");
  const damagedParts = summary.filter((s) => s.parts.length > 0)
    .map((s) => `<div style="font-family:${font};font-size:14px;color:${ink};margin:8px 0 0 0;"><span style="color:${conditionColor(s.condition)};">■</span>&nbsp;<b>${esc(PART_CONDITION[s.condition])}:</b> ${esc(s.parts.join(", "))}</div>`)
    .join("");
  const clean = summary.every((s) => s.condition === "ORIGINAL" || s.count === 0);
  const expertise = box("Ekspertiz Bilgisi", `
    <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;">${damageRows}</table>
    ${clean ? `<div style="font-family:${font};font-size:14px;font-weight:bold;color:${brand};margin:8px 0 0 0;">Boyasız, değişensiz.</div>` : damagedParts}`);

  const tramer = tramerText(v.tramerCents);
  const tramerBox = tramer ? box("Tramer Bilgisi", `<div style="font-family:${font};font-size:15px;color:${ink};">${esc(tramer)}</div>`) : "";

  const text = i.description.trim();
  const descriptionBox = text ? box("Açıklama", text.split(/\n{2,}/).map((p) =>
    `<p style="font-family:${font};font-size:15px;line-height:1.5;color:${ink};margin:0 0 8px 0;">${esc(p).replace(/\n/g, "<br>")}</p>`).join("")) : "";

  const footer = `<div style="font-family:${font};font-size:13px;color:${ink2};background:${brandLight};padding:10px 14px;">Bilgiler galerimizin kayıtlarından hazırlanmıştır. Ekspertiz ve detaylı bilgi için bizi arayabilirsiniz.</div>`;

  return `<div style="font-family:${font};color:${ink};max-width:760px;">${header}${grid}${expertise}${tramerBox}${descriptionBox}${footer}</div>`;
}

// ------------------------------------------------------------------ duz metin

export function listingPlainText(i: ListingTextInput): string {
  const v = i.vehicle;
  const out: string[] = [];
  const section = (title: string) => out.push("", title.toLocaleUpperCase("tr-TR"), "━━━━━━━━━━━━━━━━");
  // Marka / model buyutulmez: "Audi" tr-TR ile "AUDİ" olur
  out.push(`${v.make} ${v.model} · ${v.modelYear}`, i.galleryName);

  section("Araç bilgileri");
  for (const [label, value] of specs(i)) out.push(`▪ ${label}: ${value}`);

  section("Ekspertiz bilgisi");
  for (const s of damageSummary(v.parts)) {
    out.push(`▪ ${PART_CONDITION[s.condition]}: ${s.count}${s.parts.length ? ` (${s.parts.join(", ")})` : ""}`);
  }

  const tramer = tramerText(v.tramerCents);
  if (tramer) {
    section("Tramer bilgisi");
    out.push(`▪ ${tramer}`);
  }

  if (i.description.trim()) {
    section("Açıklama");
    out.push(i.description.trim());
  }

  section("Satıcı");
  out.push(i.galleryName);
  if (i.address) out.push(`Adres: ${i.address}`);
  if (i.showPhone && i.phone) out.push(`Telefon: ${formatPhone(i.phone)}`);
  return out.join("\n");
}
