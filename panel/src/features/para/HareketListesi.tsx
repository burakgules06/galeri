// Defter hareketleri. Hareket silinmez: "Düzelt" ayni tutarda ters hareket atar, asil satir gri ve ustu cizili kalir.
import { useState } from "react";
import { api } from "../../api/galeri";
import { HAREKET_TUR, kisaTarih, tl } from "../../shared/format";
import type { HareketResponse } from "../../shared/types";
import { hataMesaji } from "../../shared/useYukle";
import { Plaka } from "../../shared/ui/Plaka";
import { Buton, HataKutusu } from "../../shared/ui/Temel";

export function HareketListesi({ hareketler, yon, degisti, aracGoster = true }: {
  hareketler: HareketResponse[];
  /** +1 giris (yesil), -1 cikis (kirmizi), 0 notr */
  yon: (h: HareketResponse) => number;
  degisti: () => void;
  aracGoster?: boolean;
}) {
  return (
    <ul className="divide-y divide-cizgi">
      {hareketler.map((h) => <Satir key={h.id} h={h} yon={yon(h)} degisti={degisti} aracGoster={aracGoster} />)}
    </ul>
  );
}

function Satir({ h, yon, degisti, aracGoster }: { h: HareketResponse; yon: number; degisti: () => void; aracGoster: boolean }) {
  const [soruyor, setSoruyor] = useState(false);
  const [calisiyor, setCalisiyor] = useState(false);
  const [hata, setHata] = useState<string | null>(null);
  const duzeltme = h.duzeltilenHareketId != null;
  const iptal = h.duzeltildi;
  const baslik = h.kategori ?? (h.tur === "SERMAYE" ? `Sermaye · ${h.kaynak.ad}` : HAREKET_TUR[h.tur]);

  const duzelt = async () => {
    setCalisiyor(true);
    setHata(null);
    try {
      await api.duzelt(h.id);
      setSoruyor(false);
      degisti();
    } catch (e) {
      setHata(hataMesaji(e));
    } finally {
      setCalisiyor(false);
    }
  };

  const tutarRengi = iptal || duzeltme ? "text-murekkep-3" : yon > 0 ? "text-basari" : yon < 0 ? "text-tehlike" : "text-murekkep";
  const isaret = yon > 0 ? "+" : yon < 0 ? "−" : "";

  return (
    <li className="py-3 first:pt-0 last:pb-0">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className={`font-semibold text-[16px] ${iptal ? "line-through text-murekkep-3" : ""}`}>{baslik}</span>
            {duzeltme && <Rozet>Düzeltme</Rozet>}
            {iptal && <Rozet>Düzeltildi</Rozet>}
          </div>
          {aracGoster && h.arac && (
            <a href={`#/araclar/${h.arac.id}`} className="mt-1 flex items-center gap-2 text-[14px] text-murekkep-2 hover:text-murekkep">
              <Plaka plaka={h.arac.plaka} boyut="sm" />
              <span className="truncate">{h.arac.marka} {h.arac.model}</span>
            </a>
          )}
          <p className="text-[14px] text-murekkep-2 mt-0.5">
            {kisaTarih(h.tarih)} · {h.kaynak.ad} → {h.hedef.ad}
          </p>
          {h.aciklama && h.aciklama !== baslik && h.aciklama !== "Düzeltme" &&<p className="text-[14px] text-murekkep-3 mt-0.5">{h.aciklama}</p>}
        </div>
        <div className="text-right shrink-0">
          <p className={`font-cond font-semibold text-[19px] whitespace-nowrap ${tutarRengi} ${iptal ? "line-through" : ""}`}>
            {isaret}{isaret && " "}{tl(h.tutarKurus)}
          </p>
          {!duzeltme && !iptal && !soruyor && (
            <button type="button" onClick={() => setSoruyor(true)} className="text-[13px] font-semibold text-murekkep-2 hover:text-murekkep">
              Düzelt
            </button>
          )}
        </div>
      </div>
      {soruyor && (
        <div className="mt-2 rounded-lg bg-yuzey-2 border border-cizgi px-3 py-2 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <span className="text-[14px]">Aynı tutarda ters hareket atılacak, bu kayıt iptal görünecek.</span>
          <div className="flex gap-2 shrink-0">
            <Buton tur="sessiz" className="h-9" onClick={() => setSoruyor(false)}>Vazgeç</Buton>
            <Buton tur="tehlike" className="h-9" onClick={duzelt} disabled={calisiyor}>Düzelt</Buton>
          </div>
        </div>
      )}
      {hata && <div className="mt-2"><HataKutusu mesaj={hata} /></div>}
    </li>
  );
}

function Rozet({ children }: { children: string }) {
  return <span className="text-[12px] font-semibold px-1.5 py-0.5 rounded bg-yuzey-2 border border-cizgi text-murekkep-2">{children}</span>;
}
