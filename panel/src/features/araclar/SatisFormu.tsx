// Araci satildi olarak isaretler. Odeme plani ve tahsilat 3. adimda gelecek.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { bugunIso, tlToKurus } from "../../shared/format";
import type { AracDetayAdmin, KisiOzet, KisiResponse } from "../../shared/types";
import { hataMesaji, useYukle } from "../../shared/useYukle";
import { Alan, Buton, girdiSinifi, HataKutusu, Pencere } from "../../shared/ui/Temel";
import { KisiSecici } from "../kisiler/KisiSecici";

interface Props { acik: boolean; kapat: () => void; arac: AracDetayAdmin; kaydedildi: (d: AracDetayAdmin) => void }

export function SatisFormu(props: Props) {
  return props.acik ? <SatisFormuIcerik {...props} /> : null;
}

function SatisFormuIcerik({ kapat, arac, kaydedildi }: Props) {
  const [tarih, setTarih] = useState(bugunIso());
  const [alici, setAlici] = useState<KisiOzet | null>(null);
  const [satan, setSatan] = useState("");
  const [bedel, setBedel] = useState("");
  const [hata, setHata] = useState<string | null>(null);
  const [kaydediliyor, setKaydediliyor] = useState(false);
  const kisiler = useYukle(() => api.kisiler(), []);
  const calisanlar = useYukle(() => api.calisanlar(), []);
  const [ekKisiler, setEkKisiler] = useState<KisiResponse[]>([]);

  const gonder = async (e: FormEvent) => {
    e.preventDefault();
    const kurus = tlToKurus(bedel);
    if (Number.isNaN(kurus)) return setHata("Satış bedelini 1.400.000 gibi yaz.");
    setKaydediliyor(true);
    setHata(null);
    try {
      kaydedildi(await api.sat(arac.arac.id, { satisTarihi: tarih, aliciId: alici?.id ?? null, satanCalisanId: satan || null, satisKurus: kurus }));
    } catch (err) {
      setHata(hataMesaji(err));
    } finally {
      setKaydediliyor(false);
    }
  };

  return (
    <Pencere baslik="Satıldı olarak işaretle" acik kapat={kapat}
      alt={<>
        <Buton tur="ikincil" type="button" onClick={kapat}>Vazgeç</Buton>
        <Buton type="submit" form="satis-formu" disabled={kaydediliyor}>{kaydediliyor ? "Kaydediliyor…" : "Satışı kaydet"}</Buton>
      </>}>
      <form id="satis-formu" onSubmit={gonder} className="space-y-3">
        <HataKutusu mesaj={hata} />
        <div className="grid grid-cols-2 gap-3">
          <Alan etiket="Satış tarihi"><input type="date" className={girdiSinifi} value={tarih} min={arac.arac.alisTarihi} onChange={(e) => setTarih(e.target.value)} required /></Alan>
          <Alan etiket="Satan çalışan">
            <select className={girdiSinifi} value={satan} onChange={(e) => setSatan(e.target.value)}>
              <option value="">Seçilmedi</option>
              {(calisanlar.veri ?? []).map((c) => <option key={c.id} value={c.id}>{c.ad}</option>)}
            </select>
          </Alan>
        </div>
        <Alan etiket="Alıcı">
          <KisiSecici kisiler={[...ekKisiler, ...(kisiler.veri ?? [])]} secili={alici} onSec={setAlici}
            onYeni={(k) => setEkKisiler((l) => [k, ...l])} placeholder="İsim yaz ya da seç" />
        </Alan>
        <Alan etiket="Satış bedeli" ipucu="TL. Ödeme planı ve taksitler bir sonraki sürümde.">
          <input className={girdiSinifi} value={bedel} onChange={(e) => setBedel(e.target.value)} inputMode="decimal" placeholder="1.400.000" />
        </Alan>
      </form>
    </Pencere>
  );
}
