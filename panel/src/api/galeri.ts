import { apiFetch } from "./client";
import type {
  AracDetay, AracDetayAdmin, AracDurum, AracIstek, AracOzet, AylikAlimSatim, CalisanOzet,
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
};

export function adminMi(d: AracDetay | AracDetayAdmin): d is AracDetayAdmin {
  return "arac" in d;
}
