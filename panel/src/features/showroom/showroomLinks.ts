// Vitrin yardimcilari: adres (/vitrin/{slug}[/{aracId}]) ve WhatsApp / arama linkleri.

export function parseShowroomPath(pathname: string): { slug: string; vehicleId: string | null } | null {
  const m = pathname.match(/^\/vitrin\/([a-z0-9-]+)\/?([0-9a-f-]{36})?\/?$/i);
  return m ? { slug: m[1], vehicleId: m[2] ?? null } : null;
}

/** 0532 111 22 33 / 532... / 90532... -> 905321112233 (wa.me uluslararasi bicim ister). */
export function internationalPhone(phone: string): string {
  const d = phone.replace(/\D/g, "");
  if (d.startsWith("90") && d.length === 12) return d;
  if (d.startsWith("0") && d.length === 11) return `9${d}`;
  if (d.length === 10 && d.startsWith("5")) return `90${d}`;
  return d;
}

export function whatsappLink(phone: string, text: string): string {
  return `https://wa.me/${internationalPhone(phone)}?text=${encodeURIComponent(text)}`;
}

export function telLink(phone: string): string {
  return `tel:+${internationalPhone(phone)}`;
}
