// Masraf kategorisi: en sik kullanilanlar hizli buton, yazdikca oneri. Listede yoksa kayitta o an olusur.
import { useMemo, useState } from "react";
import { api } from "../../api/galeri";
import type { MasrafKok } from "../../shared/types";
import { useYukle } from "../../shared/useYukle";
import { girdiSinifi } from "../../shared/ui/Temel";

const HIZLI_SAYISI = 5;

export function KategoriSecici({ kok, ad, onChange }: { kok: MasrafKok; ad: string; onChange: (ad: string) => void }) {
  const { veri } = useYukle(() => api.kategoriler(kok), [kok]);
  const [acik, setAcik] = useState(false);
  const kategoriler = useMemo(() => veri ?? [], [veri]);
  const kucuk = (s: string) => s.trim().toLocaleLowerCase("tr-TR");

  const oneriler = useMemo(() => {
    const t = kucuk(ad);
    return (t ? kategoriler.filter((k) => kucuk(k.ad).includes(t)) : kategoriler).slice(0, 8);
  }, [ad, kategoriler]);
  const hizli = kategoriler.slice(0, HIZLI_SAYISI);
  const yeni = ad.trim() !== "" && !kategoriler.some((k) => kucuk(k.ad) === kucuk(ad));

  return (
    <div>
      {hizli.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-2">
          {hizli.map((k) => (
            <button key={k.id} type="button" onClick={() => onChange(k.ad)}
              className={`basilir h-8 px-3 rounded-lg text-[14px] font-semibold border ${
                kucuk(k.ad) === kucuk(ad) ? "bg-murekkep text-white border-murekkep" : "bg-yuzey text-murekkep-2 border-cizgi-2 hover:bg-yuzey-2"}`}>
              {k.ad}
            </button>
          ))}
        </div>
      )}
      <div className="relative">
        <input className={girdiSinifi} value={ad} placeholder={kok === "ARAC" ? "Boya, ekspertiz, lastik…" : "Kira, fatura, maaş…"}
          onChange={(e) => { onChange(e.target.value); setAcik(true); }}
          onFocus={() => setAcik(true)} onBlur={() => setTimeout(() => setAcik(false), 150)} />
        {acik && (oneriler.length > 0 || yeni) && (
          <ul className="absolute z-10 mt-1 w-full bg-yuzey border border-cizgi-2 rounded-lg shadow-lg max-h-64 overflow-auto py-1">
            {oneriler.map((k) => (
              <li key={k.id}>
                <button type="button" className="w-full text-left px-3 py-2 hover:bg-yuzey-2"
                  onMouseDown={(e) => e.preventDefault()} onClick={() => { onChange(k.ad); setAcik(false); }}>
                  {k.ad}
                </button>
              </li>
            ))}
            {yeni && (
              <li>
                <button type="button" className="w-full text-left px-3 py-2 text-plaka font-semibold hover:bg-plaka-acik"
                  onMouseDown={(e) => e.preventDefault()} onClick={() => { onChange(ad.trim()); setAcik(false); }}>
                  “{ad.trim()}” kategorisi olarak ekle
                </button>
              </li>
            )}
          </ul>
        )}
      </div>
      {yeni && !acik && <p className="text-[13px] text-murekkep-3 mt-1">Yeni kategori, kaydedince eklenecek.</p>}
    </div>
  );
}
