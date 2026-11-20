// Sahibinden (ve benzeri ilan siteleri) icin hazir ilan aciklamasi: bizdeki arac bilgilerinden kurumsal galeri ilanlarindaki
// gibi duzenli bir aciklama uretir. Iki bicim birlikte kopyalanir: zengin metin (HTML; aciklama editoru bicimi destekliyorsa
// tablolar ve renkler korunur) ve duz metin (desteklemiyorsa ya da telefondan yapistirilirsa bu kullanilir).
// Ilan sitesi bizim CSS'imizi gormez: renkler tema degiskenlerinden (index.css @theme) okunup satir ici yazilir.
import { BODY_PART, formatPhone, FUEL, monthYear, PART_CONDITION, TRANSMISSION, tl } from "../../shared/format";
import type { BodyPart, PartCondition, VehicleDetail } from "../../shared/types";
import { SHAPE } from "./damageShapes";
import { groupEquipment } from "../../shared/equipment";

export interface ListingTextInput {
  vehicle: VehicleDetail;
  galleryName: string;
  phone: string | null;
  address: string | null;     // dukkan adresi (vitrin ayarlari); bos ise yazilmaz
  dealerLicenseNo?: string | null;   // yetki belge no (vitrin ayarlari); bos ise kart cikmaz
  logoUrl: string | null;     // galeri logosu, tam adres (https://...); ilan sitesi resmi kabul ederse en ustte banner olur
  logoSize?: { width: number; height: number } | null;
  footerBannerUrl?: string | null;   // alt banner, tam adres; en altta logo ile ayni genislikte
  footerBannerSize?: { width: number; height: number } | null;
  contactCardUrl?: string | null;    // iletisim karti gorseli, tam adres; logonun hemen altinda, logoyla ayni en ve boyda
  contactCardSize?: { width: number; height: number } | null;   // logonun gercek boyutu: banner'a orani bozulmadan sigdirmak icin
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
  if (v.importStatus) rows.push(["İthalat", v.importStatus]);
  if (v.inspectionValidUntil) rows.push(["Muayene", monthYear(v.inspectionValidUntil)]);
  if (i.dealerLicenseNo) rows.push(["Yetki belge no", i.dealerLicenseNo]);
  return rows;
}

// ------------------------------------------------------------------ zengin metin (HTML)

// Genislik iki katmanli: stilde yuzde (width:100%, hucrelerde 25% / 50%) aciklama alanini doldurur, bilgisayarda genis
// ekrana, telefonda dar ekrana sigar. width ozelligindeki piksel deger yedektir: editor yuzde stilini silerse kartlar en
// azindan W genisliginde ve ortali kalir (ilk surumde sadece width="100%" vardi, editor onu tasimadi, kartlar daraldi).
const W = 720;                       // yedek genislik (px)
const BOX_INNER = W - 2 - 32;        // kutu icindeki alan (1 px kenarlik + 16 px ic bosluk, iki yanda)
const tableAttrs = (width: number) => `width="${width}" align="center" cellpadding="0" cellspacing="0"`;
const widthStyle = (percent: number) => `width:${percent}%;`;

// Banner gorseli (ustte logo, altta alt banner): oranini koruyarak kartin tam genisligine buyutulur, ortada durur.
// Yukseklik gorselin oranindan gelir (genis gorsel ince bir serit, kare gorsel W x W olur).
function bannerImg(url: string, galleryName: string, size: { width: number; height: number } | null | undefined): string {
  const src = esc(url);
  const alt = esc(galleryName);
  if (!size || size.width <= 0 || size.height <= 0) {
    return `<img src="${src}" alt="${alt}" width="${W}" style="display:block;margin:0 auto;width:100%;height:auto;">`;
  }
  const h = Math.round(W * size.height / size.width);
  return `<img src="${src}" alt="${alt}" width="${W}" height="${h}" style="display:block;margin:0 auto;width:100%;height:auto;">`;
}

// Gorseli verilen kutu oranina oturtur (iletisim karti logonun boyunda): genislik tam, yukseklik orandan; oran farkliysa
// ortadan kirpilir. Editor aspect-ratio'yu silerse gorsel kendi oraniyla gosterilir.
function fittedImg(url: string, galleryName: string, box: { width: number; height: number }): string {
  const h = Math.round(W * box.height / box.width);
  return `<img src="${esc(url)}" alt="${esc(galleryName)}" width="${W}" height="${h}" style="display:block;margin:0 auto;width:100%;height:auto;aspect-ratio:${box.width} / ${box.height};object-fit:cover;">`;
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
<table ${tableAttrs(W)} style="${widthStyle(100)}border-collapse:collapse;border:1px solid ${line};margin:0 auto 14px auto;">
  <tr><td style="padding:14px 16px;font-family:${font};">
    <div style="font-size:16px;font-weight:bold;color:${ink};margin:0 0 10px 0;">${esc(title)}</div>
    ${body}
  </td></tr>
</table>`;

  // Ust serit: galeri adi, arac, kisa ozet
  // En ustte galeri logosu: kartin genisligine kadar buyutulmus banner (logo yoksa yok)
  const bannerTable = (img: string, margin: string) => `
<table ${tableAttrs(W)} style="${widthStyle(100)}border-collapse:collapse;background:${white};margin:${margin};">
  <tr><td width="${W}" align="center" valign="middle" style="${widthStyle(100)}padding:0;">
    ${img}
  </td></tr>
</table>`;
  const banner = i.logoUrl ? bannerTable(bannerImg(i.logoUrl, i.galleryName, i.logoSize), "0 auto 10px auto") : "";
  // Logonun hemen altinda iletisim karti: logoyla ayni en ve boy. Kutunun orani logodan gelir; gorselin orani farkliysa
  // object-fit:cover ile ortadan kirpilir (logo yoksa kendi oraniyla)
  const contactCard = !i.contactCardUrl ? "" : bannerTable(i.logoUrl && i.logoSize
    ? fittedImg(i.contactCardUrl, i.galleryName, i.logoSize)
    : bannerImg(i.contactCardUrl, i.galleryName, i.contactCardSize), "0 auto 10px auto");
  // En altta alt banner: ustteki logo ile ayni genislik
  const footerBanner = i.footerBannerUrl
    ? bannerTable(bannerImg(i.footerBannerUrl, i.galleryName, i.footerBannerSize), "14px auto 0 auto") : "";

  // Ust satir (simsiyah zemin, beyaz yazi, tam genislik): marka ve model ayri satirlarda, satir yuksekligi acik yazili
// (editorun kendi satir yuksekligiyle iki satir ust uste biniyordu). Yil / km / yakit / vites altta bilgi kartlarinda.
  const header = `${banner}${contactCard}
<table ${tableAttrs(W)} style="${widthStyle(100)}border-collapse:collapse;margin:0 auto 14px auto;background:${ink};">
  <tr><td width="${W}" style="${widthStyle(100)}background:${ink};padding:18px 20px 2px 20px;font-family:${font};font-size:30px;line-height:36px;font-weight:bold;letter-spacing:.5px;color:${white};">${esc(v.make)}</td></tr>
  <tr><td width="${W}" style="${widthStyle(100)}background:${ink};padding:0 20px 18px 20px;font-family:${font};font-size:19px;line-height:24px;font-weight:bold;color:${white};">${esc(v.model)}</td></tr>
</table>`;

  // Bilgi kartlari: koyu baslik + acik deger, satirda 3 tane (9 kart tam 3 satir: yil, km, yakit / vites, renk, plaka /
  // ithalat, muayene, yetki belge no)
  const PER_ROW = 3;
  const cellPx = Math.floor(W / PER_ROW);
  const cellPct = Math.round(10000 / PER_ROW) / 100;
  const cells = specs(i);
  const rows: string[] = [];
  for (let r = 0; r < cells.length; r += PER_ROW) {
    const chunk = cells.slice(r, r + PER_ROW);
    while (chunk.length < PER_ROW) chunk.push(["", ""]);
    const cell = ([label, value]: [string, string], head: boolean) => label === ""
      ? `<td width="${cellPx}" style="${widthStyle(cellPct)}padding:0 3px;"></td>`
      : head
        ? `<td width="${cellPx}" align="center" style="${widthStyle(cellPct)}padding:7px 4px;background:${ink};color:${white};font-family:${font};font-size:12px;font-weight:bold;letter-spacing:.5px;border-left:3px solid ${white};border-right:3px solid ${white};">${esc(label.toLocaleUpperCase("tr-TR"))}</td>`
        : `<td width="${cellPx}" align="center" style="${widthStyle(cellPct)}padding:11px 4px;background:${soft};color:${ink};font-family:${font};font-size:16px;font-weight:bold;border-left:3px solid ${white};border-right:3px solid ${white};border-bottom:6px solid ${white};">${esc(value)}</td>`;
    rows.push(`<tr>${chunk.map((c) => cell(c, true)).join("")}</tr><tr>${chunk.map((c) => cell(c, false)).join("")}</tr>`);
  }
  const grid = `<table ${tableAttrs(W)} style="${widthStyle(100)}border-collapse:collapse;margin:0 auto 14px auto;">${rows.join("")}</table>`;

  // Ekspertiz: durum sayilari (renkli kare) ve hasarli parcalar
  const summary = damageSummary(v.parts);
  const damageRows = summary.map((s, k) => `
    <tr style="background:${k % 2 ? soft : white};">
      <td style="padding:8px 10px;font-family:${font};font-size:15px;color:${ink};"><span style="color:${conditionColor(s.condition)};font-size:16px;">■</span>&nbsp;${esc(PART_CONDITION[s.condition])}</td>
      <td width="60" align="right" style="width:60px;white-space:nowrap;padding:8px 10px;font-family:${font};font-size:15px;font-weight:bold;color:${ink};">${s.count}</td>
    </tr>`).join("");
  const damagedParts = summary.filter((s) => s.parts.length > 0)
    .map((s) => `<div style="font-family:${font};font-size:14px;color:${ink};margin:8px 0 0 0;"><span style="color:${conditionColor(s.condition)};">■</span>&nbsp;<b>${esc(PART_CONDITION[s.condition])}:</b> ${esc(s.parts.join(", "))}</div>`)
    .join("");
  const clean = summary.every((s) => s.condition === "ORIGINAL" || s.count === 0);
  const expertise = box("Ekspertiz Bilgisi", `
    <table ${tableAttrs(BOX_INNER)} style="${widthStyle(100)}border-collapse:collapse;">${damageRows}</table>
    ${clean ? `<div style="font-family:${font};font-size:14px;font-weight:bold;color:${brand};margin:8px 0 0 0;">Boyasız, değişensiz.</div>` : damagedParts}`);

  const tramer = tramerText(v.tramerCents);
  const tramerBox = tramer ? box("Tramer Bilgisi", `<div style="font-family:${font};font-size:15px;color:${ink};">${esc(tramer)}</div>`) : "";

  // Donanim: grup basligi ve altinda iki sutun
  const equipmentGroups = groupEquipment(v.equipment ?? []);
  const equipmentBox = equipmentGroups.length === 0 ? "" : box("Donanım", equipmentGroups.map((g, k) => {
    const rows: string[] = [];
    for (let r = 0; r < g.items.length; r += 2) {
      rows.push(`<tr>${[g.items[r], g.items[r + 1]].map((item) => item == null ? `<td width="${BOX_INNER / 2}" style="${widthStyle(50)}"></td>`
        : `<td width="${BOX_INNER / 2}" valign="top" style="${widthStyle(50)}padding:3px 8px 3px 0;font-family:${font};font-size:15px;color:${ink};">${esc(item)}</td>`).join("")}</tr>`);
    }
    return `<div style="font-family:${font};font-size:13px;font-weight:bold;letter-spacing:.5px;color:${ink2};margin:${k ? 12 : 0}px 0 4px 0;">${esc(g.title.toLocaleUpperCase("tr-TR"))}</div>
    <table ${tableAttrs(BOX_INNER)} style="${widthStyle(100)}border-collapse:collapse;">${rows.join("")}</table>`;
  }).join(""));

  const text = i.description.trim();
  const descriptionBox = text ? box("Açıklama", text.split(/\n{2,}/).map((p) =>
    `<p style="font-family:${font};font-size:15px;line-height:1.5;color:${ink};margin:0 0 8px 0;">${esc(p).replace(/\n/g, "<br>")}</p>`).join("")) : "";

  const footer = `<table ${tableAttrs(W)} style="${widthStyle(100)}border-collapse:collapse;margin:0 auto;"><tr><td width="${W}" style="${widthStyle(100)}font-family:${font};font-size:13px;color:${ink2};background:${brandLight};padding:10px 14px;">Bilgiler galerimizin kayıtlarından hazırlanmıştır. Ekspertiz ve detaylı bilgi için bizi arayabilirsiniz.</td></tr></table>`;

  // Sira: arac, donanim, aciklama; ekspertiz ve tramer en altta
  return `<div style="font-family:${font};color:${ink};width:100%;">${header}${grid}${equipmentBox}${descriptionBox}${expertise}${tramerBox}${footer}${footerBanner}</div>`;
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

  const equipmentGroups = groupEquipment(v.equipment ?? []);
  if (equipmentGroups.length > 0) {
    section("Donanım");
    for (const g of equipmentGroups) out.push(`▪ ${g.title}: ${g.items.join(", ")}`);
  }

  if (i.description.trim()) {
    section("Açıklama");
    out.push(i.description.trim());
  }

  section("Ekspertiz bilgisi");
  for (const s of damageSummary(v.parts)) {
    out.push(`▪ ${PART_CONDITION[s.condition]}: ${s.count}${s.parts.length ? ` (${s.parts.join(", ")})` : ""}`);
  }

  const tramer = tramerText(v.tramerCents);
  if (tramer) {
    section("Tramer bilgisi");
    out.push(`▪ ${tramer}`);
  }

  section("Satıcı");
  out.push(i.galleryName);
  if (i.address) out.push(`Adres: ${i.address}`);
  if (i.showPhone && i.phone) out.push(`Telefon: ${formatPhone(i.phone)}`);
  return out.join("\n");
}
