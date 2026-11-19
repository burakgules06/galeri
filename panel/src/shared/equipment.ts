// Arac donanimlari icin hazir liste (gruplu). Listede olmayan serbest yazilir ve "Diğer" grubunda gosterilir.
// Ayni ad iki kez eklenmez (buyuk / kucuk harf fark etmez; sunucu da kontrol eder).
export const EQUIPMENT_GROUPS: { title: string; items: string[] }[] = [
  {
    title: "Güvenlik",
    items: ["ABS", "ESP", "Şerit takip sistemi", "Kör nokta uyarısı", "Adaptif hız sabitleyici", "Hız sabitleyici",
      "Çarpışma önleme", "Yokuş kalkış desteği", "Lastik basınç sensörü", "Isofix"],
  },
  {
    title: "Park ve görüş",
    items: ["360° kamera", "Geri görüş kamerası", "Ön park sensörü", "Arka park sensörü", "Park asistanı", "LED far",
      "Xenon far", "Matrix LED far", "Yağmur sensörü", "Far sensörü", "Elektrikli katlanır aynalar"],
  },
  {
    title: "Konfor",
    items: ["Cam tavan", "Panoramik cam tavan", "Sunroof", "Deri koltuk", "Isıtmalı ön koltuklar", "Soğutmalı koltuklar",
      "Elektrikli koltuklar", "Hafızalı koltuklar", "Otomatik klima", "Çift bölgeli klima", "Anahtarsız giriş",
      "Anahtarsız çalıştırma", "Start-stop", "Elektrikli bagaj kapağı", "Isıtmalı direksiyon", "Ambiyans aydınlatma",
      "Head-up display"],
  },
  {
    title: "Multimedya",
    items: ["Navigasyon", "Apple CarPlay", "Android Auto", "Bluetooth", "Dokunmatik ekran", "Dijital gösterge paneli",
      "Premium ses sistemi", "Kablosuz şarj"],
  },
  {
    title: "Dış",
    items: ["Alaşım jant", "Spor paket", "Karartmalı camlar", "Tavan rayı", "Çeki demiri", "Spoiler"],
  },
];

const key = (s: string) => s.trim().replace(/\s+/g, " ").toLocaleLowerCase("tr-TR");

const GROUP_OF = new Map<string, string>(EQUIPMENT_GROUPS.flatMap((g) => g.items.map((i) => [key(i), g.title] as [string, string])));

/** Girilen donanimlari hazir listedeki gruplara ayirir (grup sirasiyla); listede olmayanlar "Diğer". Bos gruplar atlanir. */
export function groupEquipment(items: string[]): { title: string; items: string[] }[] {
  const groups = [...EQUIPMENT_GROUPS.map((g) => g.title), "Diğer"].map((title) => ({ title, items: [] as string[] }));
  for (const item of items) {
    const title = GROUP_OF.get(key(item)) ?? "Diğer";
    groups.find((g) => g.title === title)!.items.push(item);
  }
  return groups.filter((g) => g.items.length > 0);
}

export function sameEquipment(a: string, b: string): boolean {
  return key(a) === key(b);
}

export function isPreset(item: string): boolean {
  return GROUP_OF.has(key(item));
}
