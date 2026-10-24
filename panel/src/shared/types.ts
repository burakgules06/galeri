// Backend'in her cevabi bu zarfla gelir (GenericResponse).
export interface ApiEnvelope<T> {
  success: boolean;
  code: number;
  message: string | null;
  data: T;
  requestId: string | null;
}

export type Role = "SUPER_ADMIN" | "ADMIN" | "STAFF";

export interface LoginResponse {
  token: string;
  fullName: string;
  role: Role;
  tenantName: string;
}

// ---- Arac (backend: model/response) ----
export type Yakit = "BENZIN" | "DIZEL" | "LPG" | "BENZIN_LPG" | "HIBRIT" | "ELEKTRIK";
export type Vites = "MANUEL" | "OTOMATIK" | "YARI_OTOMATIK";
export type AracDurum = "STOKTA" | "SATILDI" | "KAPANDI";
export type ParcaDurum = "ORIJINAL" | "LOKAL_BOYALI" | "BOYALI" | "DEGISEN";
export type Parca =
  | "ON_TAMPON" | "MOTOR_KAPUTU" | "TAVAN" | "BAGAJ_KAPAGI" | "ARKA_TAMPON"
  | "SOL_ON_CAMURLUK" | "SOL_ON_KAPI" | "SOL_ARKA_KAPI" | "SOL_ARKA_CAMURLUK"
  | "SAG_ON_CAMURLUK" | "SAG_ON_KAPI" | "SAG_ARKA_KAPI" | "SAG_ARKA_CAMURLUK";
export type KisiTur = "KISI" | "KURUM";

export interface HasarOzeti { lokalBoyali: number; boyali: number; degisen: number }

export interface AracOzet {
  id: string;
  plaka: string;
  marka: string;
  model: string;
  modelYili: number;
  km: number;
  yakit: Yakit;
  vites: Vites;
  renk: string | null;
  durum: AracDurum;
  alisTarihi: string;
  satisTarihi: string | null;
  stoktaGun: number;
  hasar: HasarOzeti;
}

export interface AracDetay {
  id: string;
  plaka: string;
  marka: string;
  model: string;
  modelYili: number;
  km: number;
  yakit: Yakit;
  vites: Vites;
  renk: string | null;
  tramerKurus: number | null;
  notlar: string | null;
  durum: AracDurum;
  alisTarihi: string;
  satisTarihi: string | null;
  stoktaGun: number;
  parcalar: Record<Parca, ParcaDurum>;
}

export interface KisiOzet { id: string; ad: string; telefon: string | null }
export interface CalisanOzet { id: string; ad: string | null }

// Sadece ADMIN'e gelir. STAFF'a dogrudan AracDetay gelir.
export interface AracDetayAdmin {
  arac: AracDetay;
  satici: KisiOzet | null;
  alanCalisan: CalisanOzet | null;
  gercekAlisKurus: number | null;
  resmiAlisKurus: number | null;
  alici: KisiOzet | null;
  satanCalisan: CalisanOzet | null;
  satisKurus: number | null;
}

export interface KisiResponse { id: string; ad: string; telefon: string | null; tur: KisiTur; notlar: string | null }

export interface AylikArac { aracId: string; plaka: string; marka: string; model: string; tarih: string }
export interface CalisanAylik { calisanId: string | null; ad: string; alinanlar: AylikArac[]; satilanlar: AylikArac[] }
export interface AylikAlimSatim { yil: number; ay: number; calisanlar: CalisanAylik[] }

export interface AracIstek {
  plaka: string;
  marka: string;
  model: string;
  modelYili: number;
  yakit: Yakit;
  vites: Vites;
  km: number;
  renk: string | null;
  tramerKurus: number | null;
  notlar: string | null;
  alisTarihi: string;
  saticiId: string | null;
  alanCalisanId: string | null;
  gercekAlisKurus: number | null;
  resmiAlisKurus: number | null;
}

export interface SatisIstek { satisTarihi: string; aliciId: string | null; satanCalisanId: string | null; satisKurus: number | null }
export interface KisiIstek { ad: string; telefon: string | null; tur: KisiTur; notlar: string | null }
