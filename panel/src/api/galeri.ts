import { apiFetch } from "./client";
import type {
  AracDetay, AracDetayAdmin, AracDurum, AracIstek, AracMaliyet, AracOzet, AylikAlimSatim, CalisanOzet,
  HareketResponse, HesapResponse, KategoriResponse, MasrafIstek, MasrafKok,
  KisiIstek, KisiResponse, Parca, ParcaDurum, SatisIstek,
} from "../shared/types";

const json = (body: unknown, method = "POST"): RequestInit => ({ method, body: JSON.stringify(body) });

export const api = {
  araclar: (q: string, durum: AracDurum | null) => {
    const p = new URLSearchParams();
    if (q.trim()) p.set("q", q.trim());
    if (durum) p.set("durum", durum);
    return apiFetch<AracOzet[]>(`/api/araclar?${p}`);
  },
  arac: (id: string) => apiFetch<AracDetay | AracDetayAdmin>(`/api/araclar/${id}`),
  aracEkle: (istek: AracIstek) => apiFetch<AracDetayAdmin>("/api/araclar", json(istek)),
  aracGuncelle: (id: string, istek: AracIstek) => apiFetch<AracDetayAdmin>(`/api/araclar/${id}`, json(istek, "PUT")),
  parcalar: (id: string, parcalar: Partial<Record<Parca, ParcaDurum>>) =>
    apiFetch<AracDetayAdmin>(`/api/araclar/${id}/parcalar`, json({ parcalar }, "PUT")),
  sat: (id: string, istek: SatisIstek) => apiFetch<AracDetayAdmin>(`/api/araclar/${id}/satis`, json(istek)),
  kisiler: (q = "") => apiFetch<KisiResponse[]>(`/api/kisiler${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ""}`),
  kisiEkle: (istek: KisiIstek) => apiFetch<KisiResponse>("/api/kisiler", json(istek)),
  kisiGuncelle: (id: string, istek: KisiIstek) => apiFetch<KisiResponse>(`/api/kisiler/${id}`, json(istek, "PUT")),
  calisanlar: () => apiFetch<CalisanOzet[]>("/api/calisanlar"),
  aylik: (yil: number, ay: number) => apiFetch<AylikAlimSatim>(`/api/raporlar/aylik-alim-satim?yil=${yil}&ay=${ay}`),
  // Para defteri (sadece ADMIN)
  hesaplar: () => apiFetch<HesapResponse[]>("/api/hesaplar"),
  bankaEkle: (ad: string) => apiFetch<HesapResponse>("/api/hesaplar", json({ ad })),
  hareketler: (hesapId: string | null) => apiFetch<HareketResponse[]>(`/api/hareketler${hesapId ? `?hesapId=${hesapId}` : ""}`),
  duzelt: (id: string) => apiFetch<HareketResponse>(`/api/hareketler/${id}/duzelt`, { method: "POST" }),
  masrafEkle: (istek: MasrafIstek) => apiFetch<HareketResponse>("/api/masraflar", json(istek)),
  kategoriler: (kok: MasrafKok) => apiFetch<KategoriResponse[]>(`/api/masraf-kategorileri?kok=${kok}`),
  maliyet: (aracId: string) => apiFetch<AracMaliyet>(`/api/araclar/${aracId}/maliyet`),
};

export function adminMi(d: AracDetay | AracDetayAdmin): d is AracDetayAdmin {
  return "arac" in d;
}
