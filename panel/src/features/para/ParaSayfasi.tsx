// Kasa ve banka: bakiyeler (hareketlerden hesaplanir) ve hareket listesi. Sadece ADMIN.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { hareketYonu, tl } from "../../shared/format";
import { hataMesaji, useYukle } from "../../shared/useYukle";
import { Bos, Buton, girdiSinifi, HataKutusu } from "../../shared/ui/Temel";
import { HareketListesi } from "./HareketListesi";
import { MasrafFormu } from "./MasrafFormu";

export function ParaSayfasi() {
  const [secili, setSecili] = useState<string | null>(null);
  const [masrafAcik, setMasrafAcik] = useState(false);
  const [bankaAcik, setBankaAcik] = useState(false);
  const hesaplar = useYukle(() => api.hesaplar(), []);
  const hareketler = useYukle(() => api.hareketler(secili), [secili]);

  const yenile = () => { hesaplar.yenile(); hareketler.yenile(); };
  const toplam = (hesaplar.veri ?? []).reduce((t, h) => t + h.bakiyeKurus, 0);
  const seciliAd = hesaplar.veri?.find((h) => h.id === secili)?.ad;

  return (
    <div>
      <div className="flex items-end justify-between gap-3 mb-4">
        <div>
          <h1 className="font-cond font-bold text-[30px] leading-none tracking-tight">Kasa ve banka</h1>
          <p className="text-murekkep-2 mt-1">Toplam <span className="font-cond font-semibold text-[18px] text-murekkep">{hesaplar.veri ? tl(toplam) : "…"}</span></p>
        </div>
        <Buton onClick={() => setMasrafAcik(true)}>Masraf ekle</Buton>
      </div>

      <HataKutusu mesaj={hesaplar.hata} />

      <div className="grid grid-cols-2 md:grid-cols-4 gap-2 mb-5">
        <HesapKarti ad="Tümü" bakiye={toplam} secili={secili === null} onClick={() => setSecili(null)} />
        {(hesaplar.veri ?? []).map((h) => (
          <HesapKarti key={h.id} ad={h.ad} bakiye={h.bakiyeKurus} secili={secili === h.id} onClick={() => setSecili(h.id)} />
        ))}
        {bankaAcik
          ? <BankaFormu kapat={() => setBankaAcik(false)} eklendi={() => { setBankaAcik(false); hesaplar.yenile(); }} />
          : (
            <button type="button" onClick={() => setBankaAcik(true)}
              className="basilir rounded-xl border border-dashed border-cizgi-2 text-murekkep-2 font-semibold text-[15px] min-h-[76px] hover:bg-yuzey">
              + Banka hesabı
            </button>
          )}
      </div>

      <section className="bg-yuzey rounded-xl border border-cizgi p-4 md:p-5">
        <h2 className="font-cond font-semibold text-[20px] mb-3">{seciliAd ? `${seciliAd} hareketleri` : "Son hareketler"}</h2>
        <HataKutusu mesaj={hareketler.hata} />
        {hareketler.veri && hareketler.veri.length === 0 && (
          <Bos baslik="Henüz hareket yok" aciklama="Masraf girdiğinde ya da kasadan araç aldığında burada görünür." />
        )}
        {hareketler.veri && hareketler.veri.length > 0 && (
          <HareketListesi hareketler={hareketler.veri} yon={(h) => hareketYonu(h, secili)} degisti={yenile} />
        )}
      </section>

      <MasrafFormu acik={masrafAcik} kapat={() => setMasrafAcik(false)} kaydedildi={() => { setMasrafAcik(false); yenile(); }} />
    </div>
  );
}

function HesapKarti({ ad, bakiye, secili, onClick }: { ad: string; bakiye: number; secili: boolean; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} aria-pressed={secili}
      className={`basilir text-left rounded-xl border px-4 py-3 min-h-[76px] ${secili ? "bg-plaka-acik border-plaka" : "bg-yuzey border-cizgi hover:bg-yuzey-2"}`}>
      <span className={`block text-[14px] font-semibold truncate ${secili ? "text-plaka-koyu" : "text-murekkep-2"}`}>{ad}</span>
      <span className={`block font-cond font-bold text-[22px] leading-tight ${bakiye < 0 ? "text-tehlike" : "text-murekkep"}`}>{tl(bakiye)}</span>
    </button>
  );
}

function BankaFormu({ kapat, eklendi }: { kapat: () => void; eklendi: () => void }) {
  const [ad, setAd] = useState("");
  const [hata, setHata] = useState<string | null>(null);
  const gonder = async (e: FormEvent) => {
    e.preventDefault();
    if (!ad.trim()) return;
    try {
      await api.bankaEkle(ad.trim());
      eklendi();
    } catch (err) {
      setHata(hataMesaji(err));
    }
  };
  return (
    <form onSubmit={gonder} className="rounded-xl border border-cizgi bg-yuzey p-2 space-y-1.5 col-span-2 md:col-span-1">
      <input className={`${girdiSinifi} h-9`} value={ad} onChange={(e) => setAd(e.target.value)} placeholder="Banka adı" autoFocus />
      <div className="flex gap-1.5">
        <Buton type="button" tur="sessiz" className="h-8 grow" onClick={kapat}>Vazgeç</Buton>
        <Buton type="submit" className="h-8 grow">Ekle</Buton>
      </div>
      {hata && <p className="text-[13px] text-tehlike">{hata}</p>}
    </form>
  );
}
