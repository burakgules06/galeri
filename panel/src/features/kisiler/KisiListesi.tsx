// Musteriler, arac alinan kisiler ve kurumlar (Evim sirketi, banka). Sadece yonetici.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import type { KisiResponse, KisiTur } from "../../shared/types";
import { hataMesaji, useYukle } from "../../shared/useYukle";
import { Alan, Bos, Buton, girdiSinifi, HataKutusu, Pencere, Segment } from "../../shared/ui/Temel";

export function KisiListesi() {
  const [q, setQ] = useState("");
  const { veri, hata, yenile } = useYukle(() => api.kisiler(q), [q]);
  const [duzenlenen, setDuzenlenen] = useState<KisiResponse | "yeni" | null>(null);

  return (
    <div>
      <div className="flex items-end justify-between gap-3 mb-4">
        <h1 className="font-cond font-bold text-[30px] leading-none tracking-tight">Kişiler</h1>
        <Buton onClick={() => setDuzenlenen("yeni")}>Kişi ekle</Buton>
      </div>
      <input className={`${girdiSinifi} md:max-w-xs mb-4`} placeholder="İsim ya da telefon ara" value={q}
        onChange={(e) => setQ(e.target.value)} aria-label="Kişi ara" />
      <HataKutusu mesaj={hata} />
      {veri && veri.length === 0 && <Bos baslik={q ? "Aramaya uyan kişi yok" : "Henüz kişi yok"} aciklama="Araç alırken ya da satarken de isim yazarak ekleyebilirsin." />}
      {veri && veri.length > 0 && (
        <ul className="bg-yuzey rounded-xl border border-cizgi divide-y divide-cizgi">
          {veri.map((k) => (
            <li key={k.id}>
              <button className="w-full text-left flex items-center gap-3 px-4 py-3 hover:bg-yuzey-2" onClick={() => setDuzenlenen(k)}>
                <span className="grow">
                  <span className="font-semibold">{k.ad}</span>
                  {k.tur === "KURUM" && <span className="ml-2 text-[12px] font-semibold text-plaka bg-plaka-acik rounded px-1.5 py-0.5">Kurum</span>}
                  {k.notlar && <span className="block text-[14px] text-murekkep-3 truncate">{k.notlar}</span>}
                </span>
                <span className="font-cond text-[17px] text-murekkep-2">{k.telefon ?? ""}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {duzenlenen && (
        <KisiFormu mevcut={duzenlenen === "yeni" ? null : duzenlenen} kapat={() => setDuzenlenen(null)}
          kaydedildi={() => { setDuzenlenen(null); yenile(); }} />
      )}
    </div>
  );
}

function KisiFormu({ mevcut, kapat, kaydedildi }: { mevcut: KisiResponse | null; kapat: () => void; kaydedildi: () => void }) {
  const [ad, setAd] = useState(mevcut?.ad ?? "");
  const [telefon, setTelefon] = useState(mevcut?.telefon ?? "");
  const [tur, setTur] = useState<KisiTur>(mevcut?.tur ?? "KISI");
  const [notlar, setNotlar] = useState(mevcut?.notlar ?? "");
  const [hata, setHata] = useState<string | null>(null);

  const gonder = async (e: FormEvent) => {
    e.preventDefault();
    const istek = { ad, telefon: telefon.trim() || null, tur, notlar: notlar.trim() || null };
    try {
      await (mevcut ? api.kisiGuncelle(mevcut.id, istek) : api.kisiEkle(istek));
      kaydedildi();
    } catch (err) {
      setHata(hataMesaji(err));
    }
  };

  return (
    <Pencere baslik={mevcut ? "Kişiyi düzenle" : "Kişi ekle"} acik kapat={kapat}
      alt={<><Buton tur="ikincil" type="button" onClick={kapat}>Vazgeç</Buton><Buton type="submit" form="kisi-formu">Kaydet</Buton></>}>
      <form id="kisi-formu" onSubmit={gonder} className="space-y-3">
        <HataKutusu mesaj={hata} />
        <Alan etiket="Tür">
          <Segment<KisiTur> deger={tur} onChange={setTur} secenekler={[{ deger: "KISI", etiket: "Kişi" }, { deger: "KURUM", etiket: "Kurum (Evim, banka)" }]} />
        </Alan>
        <Alan etiket="Ad soyad"><input className={girdiSinifi} value={ad} onChange={(e) => setAd(e.target.value)} required /></Alan>
        <Alan etiket="Telefon"><input className={girdiSinifi} value={telefon} onChange={(e) => setTelefon(e.target.value)} inputMode="tel" placeholder="0530 111 22 33" /></Alan>
        <Alan etiket="Notlar"><textarea className={`${girdiSinifi} h-20 py-2`} value={notlar} onChange={(e) => setNotlar(e.target.value)} /></Alan>
      </form>
    </Pencere>
  );
}
