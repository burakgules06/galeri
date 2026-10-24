// Arac ekleme / duzenleme. Sadece ADMIN acar. Tutarlar TL girilir, kurus gonderilir.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { bugunIso, kurusToInput, plakaBicimle, tlToKurus, VITES, YAKIT } from "../../shared/format";
import type { AracDetayAdmin, AracIstek, KisiOzet, KisiResponse, Vites, Yakit } from "../../shared/types";
import { hataMesaji, useYukle } from "../../shared/useYukle";
import { Alan, Buton, girdiSinifi, HataKutusu, Pencere, Segment } from "../../shared/ui/Temel";
import { KisiSecici } from "../kisiler/KisiSecici";

interface Props {
  acik: boolean;
  kapat: () => void;
  kaydedildi: (d: AracDetayAdmin) => void;
  mevcut?: AracDetayAdmin;
}

export function AracFormu(props: Props) {
  return props.acik ? <AracFormuIcerik {...props} /> : null;
}

function AracFormuIcerik({ kapat, kaydedildi, mevcut }: Props) {
  const a = mevcut?.arac;
  const [plaka, setPlaka] = useState(a?.plaka ?? "");
  const [marka, setMarka] = useState(a?.marka ?? "");
  const [model, setModel] = useState(a?.model ?? "");
  const [yil, setYil] = useState(a ? String(a.modelYili) : "");
  const [yakit, setYakit] = useState<Yakit>(a?.yakit ?? "BENZIN");
  const [vites, setVites] = useState<Vites>(a?.vites ?? "MANUEL");
  const [km, setKm] = useState(a ? String(a.km) : "");
  const [renk, setRenk] = useState(a?.renk ?? "");
  const [tramer, setTramer] = useState(kurusToInput(a?.tramerKurus));
  const [notlar, setNotlar] = useState(a?.notlar ?? "");
  const [alisTarihi, setAlisTarihi] = useState(a?.alisTarihi ?? bugunIso());
  const [satici, setSatici] = useState<KisiOzet | null>(mevcut?.satici ?? null);
  const [alanCalisan, setAlanCalisan] = useState(mevcut?.alanCalisan?.id ?? "");
  const [gercek, setGercek] = useState(kurusToInput(mevcut?.gercekAlisKurus));
  const [resmi, setResmi] = useState(kurusToInput(mevcut?.resmiAlisKurus));
  const [hata, setHata] = useState<string | null>(null);
  const [kaydediliyor, setKaydediliyor] = useState(false);

  const kisiler = useYukle(() => api.kisiler(), []);
  const calisanlar = useYukle(() => api.calisanlar(), []);
  const [ekKisiler, setEkKisiler] = useState<KisiResponse[]>([]);
  const tumKisiler = [...ekKisiler, ...(kisiler.veri ?? [])];

  const gonder = async (e: FormEvent) => {
    e.preventDefault();
    const tutarlar = [tlToKurus(tramer), tlToKurus(gercek), tlToKurus(resmi)];
    if (tutarlar.some((t) => Number.isNaN(t))) return setHata("Tutarları 1.250.000 gibi yaz.");
    const yilSayi = Number(yil), kmSayi = Number(km.replace(/\./g, ""));
    if (!Number.isInteger(yilSayi) || yilSayi < 1950 || yilSayi > 2100) return setHata("Model yılını kontrol et.");
    if (!Number.isInteger(kmSayi) || kmSayi < 0) return setHata("Kilometreyi kontrol et.");

    const istek: AracIstek = {
      plaka, marka, model, modelYili: yilSayi, yakit, vites, km: kmSayi,
      renk: renk.trim() || null, tramerKurus: tutarlar[0], notlar: notlar.trim() || null,
      alisTarihi, saticiId: satici?.id ?? null, alanCalisanId: alanCalisan || null,
      gercekAlisKurus: tutarlar[1], resmiAlisKurus: tutarlar[2],
    };
    setKaydediliyor(true);
    setHata(null);
    try {
      kaydedildi(a ? await api.aracGuncelle(a.id, istek) : await api.aracEkle(istek));
    } catch (err) {
      setHata(hataMesaji(err));
    } finally {
      setKaydediliyor(false);
    }
  };

  return (
    <Pencere
      baslik={a ? "Aracı düzenle" : "Araç ekle"}
      acik
      kapat={kapat}
      alt={<>
        <Buton tur="ikincil" type="button" onClick={kapat}>Vazgeç</Buton>
        <Buton type="submit" form="arac-formu" disabled={kaydediliyor}>{kaydediliyor ? "Kaydediliyor…" : "Kaydet"}</Buton>
      </>}
    >
      <form id="arac-formu" onSubmit={gonder} className="space-y-6">
        <HataKutusu mesaj={hata} />

        <section className="space-y-3">
          <h3 className="font-cond font-semibold text-[18px]">Araç</h3>
          <Alan etiket="Plaka">
            <input className={`${girdiSinifi} font-cond font-bold tracking-[0.06em] text-[18px]`} value={plaka}
              onChange={(e) => setPlaka(plakaBicimle(e.target.value))} required autoCapitalize="characters" placeholder="34 ABC 123" />
          </Alan>
          <div className="grid grid-cols-2 gap-3">
            <Alan etiket="Marka"><input className={girdiSinifi} value={marka} onChange={(e) => setMarka(e.target.value)} required placeholder="Volkswagen" /></Alan>
            <Alan etiket="Model yılı"><input className={girdiSinifi} value={yil} onChange={(e) => setYil(e.target.value)} required inputMode="numeric" placeholder="2021" /></Alan>
          </div>
          <Alan etiket="Model"><input className={girdiSinifi} value={model} onChange={(e) => setModel(e.target.value)} required placeholder="Passat 1.5 TSI Business" /></Alan>
          <Alan etiket="Yakıt">
            <Segment<Yakit> deger={yakit} onChange={setYakit} secenekler={(Object.keys(YAKIT) as Yakit[]).map((d) => ({ deger: d, etiket: YAKIT[d] }))} />
          </Alan>
          <Alan etiket="Vites">
            <Segment<Vites> deger={vites} onChange={setVites} secenekler={(Object.keys(VITES) as Vites[]).map((d) => ({ deger: d, etiket: VITES[d] }))} />
          </Alan>
          <div className="grid grid-cols-2 gap-3">
            <Alan etiket="Kilometre"><input className={girdiSinifi} value={km} onChange={(e) => setKm(e.target.value)} required inputMode="numeric" placeholder="64000" /></Alan>
            <Alan etiket="Renk"><input className={girdiSinifi} value={renk} onChange={(e) => setRenk(e.target.value)} placeholder="Beyaz" /></Alan>
          </div>
          <Alan etiket="Tramer (hasar kaydı)" ipucu="TL, yoksa boş bırak">
            <input className={girdiSinifi} value={tramer} onChange={(e) => setTramer(e.target.value)} inputMode="decimal" placeholder="18.500" />
          </Alan>
        </section>

        <section className="space-y-3">
          <h3 className="font-cond font-semibold text-[18px]">Alış</h3>
          <div className="grid grid-cols-2 gap-3">
            <Alan etiket="Alış tarihi"><input type="date" className={girdiSinifi} value={alisTarihi} onChange={(e) => setAlisTarihi(e.target.value)} required /></Alan>
            <Alan etiket="Alan çalışan">
              <select className={girdiSinifi} value={alanCalisan} onChange={(e) => setAlanCalisan(e.target.value)}>
                <option value="">Seçilmedi</option>
                {(calisanlar.veri ?? []).map((c) => <option key={c.id} value={c.id}>{c.ad}</option>)}
              </select>
            </Alan>
          </div>
          <Alan etiket="Kimden alındı">
            <KisiSecici kisiler={tumKisiler} secili={satici} onSec={setSatici}
              onYeni={(k) => setEkKisiler((l) => [k, ...l])} placeholder="İsim yaz ya da seç" />
          </Alan>
          <div className="grid grid-cols-2 gap-3">
            <Alan etiket="Gerçek alış bedeli" ipucu="Kâr bununla hesaplanır">
              <input className={girdiSinifi} value={gercek} onChange={(e) => setGercek(e.target.value)} inputMode="decimal" placeholder="1.250.000" />
            </Alan>
            <Alan etiket="Resmi alış bedeli" ipucu="Belgedeki tutar">
              <input className={girdiSinifi} value={resmi} onChange={(e) => setResmi(e.target.value)} inputMode="decimal" placeholder="980.000" />
            </Alan>
          </div>
        </section>

        <Alan etiket="Notlar">
          <textarea className={`${girdiSinifi} h-24 py-2`} value={notlar} onChange={(e) => setNotlar(e.target.value)} />
        </Alan>
      </form>
    </Pencere>
  );
}
