// Veri yukleme: { veri, yukleniyor, hata, yenile }. Bagimliliklar degisince yeniden yukler.
// setState sadece promise sonucunda cagrilir (effect icinde senkron setState yok).
import { useCallback, useEffect, useState } from "react";
import { ApiError } from "./errors/ApiError";

export function hataMesaji(e: unknown): string {
  return e instanceof ApiError ? e.message : "Beklenmeyen bir hata oluştu";
}

interface Durum<T> { anahtar: string; veri: T | null; hata: string | null }

export function useYukle<T>(yukleyici: () => Promise<T>, bagimliliklar: unknown[]) {
  const [sayac, setSayac] = useState(0);
  const anahtar = JSON.stringify([...bagimliliklar, sayac]);
  const [durum, setDurum] = useState<Durum<T>>({ anahtar: "", veri: null, hata: null });

  useEffect(() => {
    let iptal = false;
    yukleyici()
      .then((v) => { if (!iptal) setDurum({ anahtar, veri: v, hata: null }); })
      .catch((e) => { if (!iptal) setDurum((d) => ({ anahtar, veri: d.veri, hata: hataMesaji(e) })); });
    return () => { iptal = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [anahtar]);

  const yenile = useCallback(() => setSayac((s) => s + 1), []);
  const setVeri = useCallback((v: T) => setDurum((d) => ({ ...d, veri: v })), []);
  return { veri: durum.veri, setVeri, yukleniyor: durum.anahtar !== anahtar, hata: durum.hata, yenile };
}
