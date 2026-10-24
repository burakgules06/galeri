// Masraf girisi: araca bagliysa aracin maliyetine eklenir, degilse genel gider. Odeme kasadan ya da bankadan.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { bugunIso, tl, tlToKurus } from "../../shared/format";
import type { HareketResponse, MasrafKok } from "../../shared/types";
import { hataMesaji, useYukle } from "../../shared/useYukle";
import { Alan, Buton, girdiSinifi, HataKutusu, Pencere, Segment } from "../../shared/ui/Temel";
import { KategoriSecici } from "./KategoriSecici";

interface AracSecimi { id: string; plaka: string; marka: string; model: string }

interface Props {
  acik: boolean;
  kapat: () => void;
  kaydedildi: (h: HareketResponse) => void;
  /** Arac detayindan acilinca arac sabittir. */
  arac?: AracSecimi;
}

export function MasrafFormu(props: Props) {
  return props.acik ? <MasrafFormuIcerik {...props} /> : null;
}

function MasrafFormuIcerik({ kapat, kaydedildi, arac }: Props) {
  const [kok, setKok] = useState<MasrafKok>(arac ? "ARAC" : "GENEL");
  const [aracId, setAracId] = useState(arac?.id ?? "");
  const [tutar, setTutar] = useState("");
  const [kategori, setKategori] = useState("");
  const [hesapId, setHesapId] = useState("");
  const [tarih, setTarih] = useState(bugunIso());
  const [aciklama, setAciklama] = useState("");
  const [hata, setHata] = useState<string | null>(null);
  const [kaydediliyor, setKaydediliyor] = useState(false);

  const hesaplar = useYukle(() => api.hesaplar(), []);
  const stok = useYukle(() => (arac ? Promise.resolve([]) : api.araclar("", "STOKTA")), [arac?.id]);
  const secilenHesap = hesapId || hesaplar.veri?.[0]?.id || "";

  const gonder = async (e: FormEvent) => {
    e.preventDefault();
    const kurus = tlToKurus(tutar);
    if (kurus == null || Number.isNaN(kurus) || kurus <= 0) return setHata("Tutarı 5.000 gibi yaz.");
    if (!kategori.trim()) return setHata("Kategori yaz ya da seç.");
    if (kok === "ARAC" && !aracId) return setHata("Aracı seç.");
    if (!secilenHesap) return setHata("Ödemenin çıktığı hesabı seç.");
    setKaydediliyor(true);
    setHata(null);
    try {
      kaydedildi(await api.masrafEkle({
        tarih, kaynakHesapId: secilenHesap, tutarKurus: kurus,
        aracId: kok === "ARAC" ? aracId : null, kategoriId: null, kategoriAd: kategori.trim(),
        aciklama: aciklama.trim() || null,
      }));
    } catch (err) {
      setHata(hataMesaji(err));
    } finally {
      setKaydediliyor(false);
    }
  };

  return (
    <Pencere baslik={arac ? `Masraf ekle · ${arac.plaka}` : "Masraf ekle"} acik kapat={kapat}
      alt={<>
        <Buton tur="ikincil" type="button" onClick={kapat}>Vazgeç</Buton>
        <Buton type="submit" form="masraf-formu" disabled={kaydediliyor}>{kaydediliyor ? "Kaydediliyor…" : "Masrafı kaydet"}</Buton>
      </>}>
      <form id="masraf-formu" onSubmit={gonder} className="space-y-4">
        <HataKutusu mesaj={hata} />

        {!arac && (
          <Alan etiket="Masraf türü">
            <Segment<MasrafKok> deger={kok} onChange={(k) => { setKok(k); setKategori(""); }}
              secenekler={[{ deger: "GENEL", etiket: "Genel gider" }, { deger: "ARAC", etiket: "Araca bağlı" }]} />
          </Alan>
        )}

        {!arac && kok === "ARAC" && (
          <Alan etiket="Araç" ipucu="Masraf aracın maliyetine eklenir">
            <select className={girdiSinifi} value={aracId} onChange={(e) => setAracId(e.target.value)}>
              <option value="">Seç</option>
              {(stok.veri ?? []).map((a) => <option key={a.id} value={a.id}>{a.plaka} · {a.marka} {a.model}</option>)}
            </select>
          </Alan>
        )}

        <Alan etiket="Tutar" ipucu="TL">
          <input className={`${girdiSinifi} font-cond text-[18px]`} value={tutar} onChange={(e) => setTutar(e.target.value)}
            inputMode="decimal" placeholder="5.000" autoFocus />
        </Alan>

        <Alan etiket="Kategori">
          <KategoriSecici key={kok} kok={kok} ad={kategori} onChange={setKategori} />
        </Alan>

        <Alan etiket="Nereden ödendi">
          {hesaplar.veri && (
            <Segment<string> deger={secilenHesap} onChange={setHesapId}
              secenekler={hesaplar.veri.map((h) => ({ deger: h.id, etiket: `${h.ad} · ${tl(h.bakiyeKurus)}` }))} />
          )}
        </Alan>

        <div className="grid grid-cols-2 gap-3">
          <Alan etiket="Tarih"><input type="date" className={girdiSinifi} value={tarih} onChange={(e) => setTarih(e.target.value)} required /></Alan>
          <Alan etiket="Açıklama"><input className={girdiSinifi} value={aciklama} onChange={(e) => setAciklama(e.target.value)} placeholder="İsteğe bağlı" /></Alan>
        </div>
      </form>
    </Pencere>
  );
}
