// Calisan bazinda aylik alim-satim. Tutar yok. Yonetici herkesi, calisan sadece kendini gorur.
import { useState } from "react";
import { api } from "../../api/galeri";
import { AYLAR, kisaTarih } from "../../shared/format";
import type { AylikArac, CalisanAylik } from "../../shared/types";
import { useYukle } from "../../shared/useYukle";
import { Plaka } from "../../shared/ui/Plaka";
import { Bos, HataKutusu } from "../../shared/ui/Temel";

export function AylikGorunum() {
  const simdi = new Date();
  const [donem, setDonem] = useState({ yil: simdi.getFullYear(), ay: simdi.getMonth() + 1 });
  const { veri, hata } = useYukle(() => api.aylik(donem.yil, donem.ay), [donem.yil, donem.ay]);

  const kaydir = (fark: number) => setDonem(({ yil, ay }) => {
    const t = new Date(yil, ay - 1 + fark, 1);
    return { yil: t.getFullYear(), ay: t.getMonth() + 1 };
  });

  const toplamAlis = veri?.calisanlar.reduce((s, c) => s + c.alinanlar.length, 0) ?? 0;
  const toplamSatis = veri?.calisanlar.reduce((s, c) => s + c.satilanlar.length, 0) ?? 0;

  return (
    <div>
      <div className="flex items-end justify-between gap-3 mb-4">
        <h1 className="font-cond font-bold text-[30px] leading-none tracking-tight">Alım ve satım</h1>
      </div>

      <div className="flex flex-wrap items-center gap-x-2 gap-y-3 mb-4">
        <OkButon yon="geri" onClick={() => kaydir(-1)} />
        <p className="font-cond font-bold text-[24px] min-w-[160px] text-center">{AYLAR[donem.ay - 1]} {donem.yil}</p>
        <OkButon yon="ileri" onClick={() => kaydir(1)} />
        {veri && (
          <p className="w-full md:w-auto md:ml-auto text-murekkep-2 text-[15px]">
            <span className="font-cond font-bold text-murekkep text-[19px]">{toplamAlis}</span> alındı,{" "}
            <span className="font-cond font-bold text-murekkep text-[19px]">{toplamSatis}</span> satıldı
          </p>
        )}
      </div>

      <HataKutusu mesaj={hata} />
      {veri && veri.calisanlar.length === 0 && <Bos baslik="Bu ay kayıt yok" />}

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {veri?.calisanlar.map((c) => <CalisanKarti key={c.calisanId ?? "yok"} c={c} />)}
      </div>
    </div>
  );
}

function CalisanKarti({ c }: { c: CalisanAylik }) {
  const [acik, setAcik] = useState(false);
  const bos = c.alinanlar.length === 0 && c.satilanlar.length === 0;
  return (
    <section className="bg-yuzey rounded-xl border border-cizgi">
      <button type="button" onClick={() => setAcik((x) => !x)} disabled={bos}
        className="basilir w-full flex items-center gap-4 p-4 text-left disabled:cursor-default" aria-expanded={acik}>
        <span className="w-11 h-11 rounded-full bg-plaka-acik text-plaka-koyu font-cond font-bold text-[18px] grid place-items-center shrink-0">
          {c.ad.split(" ").map((s) => s[0]).slice(0, 2).join("").toLocaleUpperCase("tr-TR")}
        </span>
        <span className="grow font-semibold text-[17px] truncate">{c.ad}</span>
        <Sayi n={c.alinanlar.length} etiket="aldı" />
        <Sayi n={c.satilanlar.length} etiket="sattı" />
      </button>
      {acik && (
        <div className="border-t border-cizgi px-4 py-3 space-y-3">
          <Liste baslik="Aldıkları" araclar={c.alinanlar} />
          <Liste baslik="Sattıkları" araclar={c.satilanlar} />
        </div>
      )}
    </section>
  );
}

function Sayi({ n, etiket }: { n: number; etiket: string }) {
  return (
    <span className="text-center w-12">
      <span className={`block font-cond font-bold text-[26px] leading-none ${n ? "text-murekkep" : "text-murekkep-3"}`}>{n}</span>
      <span className="block text-[12px] text-murekkep-2">{etiket}</span>
    </span>
  );
}

function Liste({ baslik, araclar }: { baslik: string; araclar: AylikArac[] }) {
  if (!araclar.length) return null;
  return (
    <div>
      <h3 className="text-[13px] font-semibold text-murekkep-2 mb-1.5">{baslik}</h3>
      <ul className="space-y-1.5">
        {araclar.map((a) => (
          <li key={a.aracId}>
            <a href={`#/araclar/${a.aracId}`} className="flex items-center gap-2.5 hover:bg-yuzey-2 rounded-lg -mx-1 px-1 py-1">
              <Plaka plaka={a.plaka} boyut="sm" />
              <span className="grow truncate text-[15px]">{a.marka} {a.model}</span>
              <span className="text-[13px] text-murekkep-3">{kisaTarih(a.tarih)}</span>
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}

function OkButon({ yon, onClick }: { yon: "geri" | "ileri"; onClick: () => void }) {
  return (
    <button onClick={onClick} aria-label={yon === "geri" ? "Önceki ay" : "Sonraki ay"}
      className="basilir w-10 h-10 rounded-lg bg-yuzey border border-cizgi grid place-items-center hover:bg-yuzey-2">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
        <path d={yon === "geri" ? "M15 6l-6 6 6 6" : "M9 6l6 6-6 6"} />
      </svg>
    </button>
  );
}
