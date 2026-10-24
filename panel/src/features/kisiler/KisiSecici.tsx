// Ara-sec kutusu: yazdikca kisileri suzer; listede yoksa "<ad> yeni kisi olarak ekle" ile aninda olusturur.
import { useMemo, useState } from "react";
import { api } from "../../api/galeri";
import type { KisiOzet, KisiResponse } from "../../shared/types";
import { girdiSinifi } from "../../shared/ui/Temel";

export function KisiSecici({ kisiler, secili, onSec, onYeni, placeholder }: {
  kisiler: KisiResponse[];
  secili: KisiOzet | null;
  onSec: (k: KisiOzet | null) => void;
  onYeni: (k: KisiResponse) => void;
  placeholder: string;
}) {
  const [metin, setMetin] = useState("");
  const [acik, setAcik] = useState(false);
  const [ekleniyor, setEkleniyor] = useState(false);

  const sonuclar = useMemo(() => {
    const t = metin.trim().toLocaleLowerCase("tr-TR");
    return (t ? kisiler.filter((k) => k.ad.toLocaleLowerCase("tr-TR").includes(t) || (k.telefon ?? "").includes(t)) : kisiler).slice(0, 8);
  }, [metin, kisiler]);

  if (secili) {
    return (
      <div className={`${girdiSinifi} flex items-center justify-between`}>
        <span className="truncate">{secili.ad}</span>
        <button type="button" className="text-[14px] text-plaka font-semibold" onClick={() => onSec(null)}>Değiştir</button>
      </div>
    );
  }

  const yeniEkle = async () => {
    const ad = metin.trim();
    if (!ad) return;
    setEkleniyor(true);
    try {
      const k = await api.kisiEkle({ ad, telefon: null, tur: "KISI", notlar: null });
      onYeni(k);
      onSec({ id: k.id, ad: k.ad, telefon: k.telefon });
      setMetin("");
      setAcik(false);
    } finally {
      setEkleniyor(false);
    }
  };

  const tamEslesme = sonuclar.some((k) => k.ad.toLocaleLowerCase("tr-TR") === metin.trim().toLocaleLowerCase("tr-TR"));

  return (
    <div className="relative">
      <input
        className={girdiSinifi}
        placeholder={placeholder}
        value={metin}
        onChange={(e) => { setMetin(e.target.value); setAcik(true); }}
        onFocus={() => setAcik(true)}
        onBlur={() => setTimeout(() => setAcik(false), 150)}
      />
      {acik && (sonuclar.length > 0 || metin.trim()) && (
        <ul className="absolute z-10 mt-1 w-full bg-yuzey border border-cizgi-2 rounded-lg shadow-lg max-h-64 overflow-auto py-1">
          {sonuclar.map((k) => (
            <li key={k.id}>
              <button type="button" className="w-full text-left px-3 py-2 hover:bg-yuzey-2"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => { onSec({ id: k.id, ad: k.ad, telefon: k.telefon }); setMetin(""); setAcik(false); }}>
                <span className="font-medium">{k.ad}</span>
                {k.telefon && <span className="text-murekkep-3 text-[14px] ml-2">{k.telefon}</span>}
              </button>
            </li>
          ))}
          {metin.trim() && !tamEslesme && (
            <li>
              <button type="button" disabled={ekleniyor} className="w-full text-left px-3 py-2 text-plaka font-semibold hover:bg-plaka-acik"
                onMouseDown={(e) => e.preventDefault()} onClick={yeniEkle}>
                “{metin.trim()}” yeni kişi olarak ekle
              </button>
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
