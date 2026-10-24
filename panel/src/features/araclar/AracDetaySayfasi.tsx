import { useState, type ReactNode } from "react";
import { adminMi, api } from "../../api/galeri";
import { DURUM, sayi, tarih, tl, VITES, YAKIT } from "../../shared/format";
import type { AracDetay, AracDetayAdmin, Parca, ParcaDurum } from "../../shared/types";
import { hataMesaji, useYukle } from "../../shared/useYukle";
import { Plaka } from "../../shared/ui/Plaka";
import { Buton, HataKutusu } from "../../shared/ui/Temel";
import { AracFormu } from "./AracFormu";
import { HasarSemasi } from "./HasarSemasi";
import { SatisFormu } from "./SatisFormu";
import { StoktaGun } from "./StoktaGun";
import { HareketListesi } from "../para/HareketListesi";
import { MasrafFormu } from "../para/MasrafFormu";

export function AracDetaySayfasi({ id }: { id: string }) {
  const { veri, setVeri, hata } = useYukle(() => api.arac(id), [id]);
  const [form, setForm] = useState<"duzenle" | "sat" | null>(null);

  if (hata) return <HataKutusu mesaj={hata} />;
  if (!veri) return <p className="text-murekkep-2 py-10 text-center">Yükleniyor…</p>;

  const admin = adminMi(veri) ? veri : null;
  const a: AracDetay = admin ? admin.arac : (veri as AracDetay);

  return (
    <div>
      <a href="#/araclar" className="inline-flex items-center gap-1 text-murekkep-2 font-semibold text-[15px] mb-3 hover:text-murekkep">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M15 6l-6 6 6 6" /></svg>
        Araçlar
      </a>

      <header className="flex flex-col md:flex-row md:items-end md:justify-between gap-4 mb-5">
        <div>
          <Plaka plaka={a.plaka} boyut="lg" />
          <h1 className="font-cond font-bold text-[30px] leading-tight tracking-tight mt-3">{a.marka} {a.model}</h1>
          <div className="flex items-center gap-3 mt-1 text-murekkep-2">
            <span>{a.modelYili}</span>
            <span className="font-semibold text-murekkep">{DURUM[a.durum]}</span>
            <StoktaGun gun={a.stoktaGun} durum={a.durum} />
          </div>
        </div>
        {admin && (
          <div className="flex gap-2">
            <Buton tur="ikincil" onClick={() => setForm("duzenle")}>Düzenle</Buton>
            {a.durum === "STOKTA" && <Buton onClick={() => setForm("sat")}>Satıldı olarak işaretle</Buton>}
          </div>
        )}
      </header>

      <div className="grid gap-4 lg:grid-cols-[1fr_380px]">
        <div className="space-y-4">
          <Kart baslik="Teknik bilgiler">
            <dl className="grid grid-cols-2 sm:grid-cols-3 gap-x-4 gap-y-3">
              <Bilgi etiket="Kilometre" deger={`${sayi(a.km)} km`} sayi />
              <Bilgi etiket="Yakıt" deger={YAKIT[a.yakit]} />
              <Bilgi etiket="Vites" deger={VITES[a.vites]} />
              <Bilgi etiket="Renk" deger={a.renk ?? "—"} />
              <Bilgi etiket="Tramer" deger={a.tramerKurus ? tl(a.tramerKurus) : "Yok"} sayi />
              <Bilgi etiket="Alış tarihi" deger={tarih(a.alisTarihi)} />
              {a.satisTarihi && <Bilgi etiket="Satış tarihi" deger={tarih(a.satisTarihi)} />}
            </dl>
            {a.notlar && <p className="mt-4 pt-3 border-t border-cizgi text-[15px] whitespace-pre-line">{a.notlar}</p>}
          </Kart>

          {admin && (
            <Kart baslik="Alış ve satış">
              <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
                <Bilgi etiket="Kimden alındı" deger={admin.satici?.ad ?? "—"} />
                <Bilgi etiket="Alan çalışan" deger={admin.alanCalisan?.ad ?? "—"} />
                <Bilgi etiket="Gerçek alış bedeli" deger={tl(admin.gercekAlisKurus)} sayi vurgu />
                <Bilgi etiket="Resmi alış bedeli" deger={tl(admin.resmiAlisKurus)} sayi />
                {a.durum !== "STOKTA" && <>
                  <Bilgi etiket="Alıcı" deger={admin.alici?.ad ?? "—"} />
                  <Bilgi etiket="Satan çalışan" deger={admin.satanCalisan?.ad ?? "—"} />
                  <Bilgi etiket="Satış bedeli" deger={tl(admin.satisKurus)} sayi vurgu />
                </>}
              </dl>
            </Kart>
          )}

          {admin && (
            <MaliyetKarti key={`${admin.gercekAlisKurus}-${admin.satisKurus}-${admin.alisHesapId}`} arac={a} />
          )}
        </div>

        <Kart baslik="Hasar durumu">
          <HasarDuzenleyici
            key={JSON.stringify(a.parcalar)}
            aracId={a.id}
            parcalar={a.parcalar}
            duzenlenebilir={!!admin}
            kaydedildi={setVeri}
          />
        </Kart>
      </div>

      {admin && <AracFormu acik={form === "duzenle"} kapat={() => setForm(null)} mevcut={admin}
        kaydedildi={(d) => { setVeri(d); setForm(null); }} />}
      {admin && <SatisFormu acik={form === "sat"} kapat={() => setForm(null)} arac={admin}
        kaydedildi={(d) => { setVeri(d); setForm(null); }} />}
    </div>
  );
}

// Maliyet = gercek alis + araca bagli masraflar (defterden). Kar sadece satilmis aracta.
function MaliyetKarti({ arac }: { arac: AracDetay }) {
  const { veri, hata, yenile } = useYukle(() => api.maliyet(arac.id), [arac.id]);
  const [masrafAcik, setMasrafAcik] = useState(false);
  const masraflar = (veri?.hareketler ?? []).filter((h) => h.tur === "ARAC_MASRAF");

  return (
    <Kart baslik="Maliyet">
      <HataKutusu mesaj={hata} />
      {veri && (
        <>
          <dl className="grid grid-cols-2 sm:grid-cols-3 gap-x-4 gap-y-3">
            <div>
              <dt className="text-[13px] text-murekkep-2">Alış</dt>
              <dd className="font-cond text-[19px] font-medium">{tl(veri.alisKurus)}</dd>
              {!veri.alisDefterde && veri.alisKurus > 0 && <dd className="text-[13px] text-uyari">Ödemesi kasaya işlenmedi</dd>}
            </div>
            <Bilgi etiket="Masraflar" deger={tl(veri.masrafKurus)} sayi />
            <Bilgi etiket="Toplam maliyet" deger={tl(veri.toplamKurus)} sayi vurgu />
            {veri.karKurus != null && <>
              <Bilgi etiket="Satış bedeli" deger={tl(veri.satisKurus)} sayi />
              <div>
                <dt className="text-[13px] text-murekkep-2">{veri.karKurus >= 0 ? "Kâr" : "Zarar"}</dt>
                <dd className={`font-cond text-[19px] font-bold ${veri.karKurus >= 0 ? "text-basari" : "text-tehlike"}`}>{tl(Math.abs(veri.karKurus))}</dd>
              </div>
            </>}
          </dl>

          <div className="mt-4 pt-3 border-t border-cizgi">
            <div className="flex items-center justify-between mb-2">
              <h3 className="font-semibold text-[15px] text-murekkep-2">Masraf dökümü</h3>
              <Buton tur="ikincil" className="h-9" onClick={() => setMasrafAcik(true)}>Masraf ekle</Buton>
            </div>
            {masraflar.length === 0
              ? <p className="text-[14px] text-murekkep-3">Bu araca henüz masraf girilmedi.</p>
              : <HareketListesi hareketler={masraflar} yon={() => 0} degisti={yenile} aracGoster={false} />}
          </div>
        </>
      )}
      <MasrafFormu acik={masrafAcik} kapat={() => setMasrafAcik(false)} arac={arac}
        kaydedildi={() => { setMasrafAcik(false); yenile(); }} />
    </Kart>
  );
}

function HasarDuzenleyici({ aracId, parcalar, duzenlenebilir, kaydedildi }: {
  aracId: string; parcalar: Record<Parca, ParcaDurum>; duzenlenebilir: boolean; kaydedildi: (d: AracDetayAdmin) => void;
}) {
  const [taslak, setTaslak] = useState(parcalar);
  const [kaydediliyor, setKaydediliyor] = useState(false);
  const [hata, setHata] = useState<string | null>(null);
  const degisti = (Object.keys(parcalar) as Parca[]).filter((p) => parcalar[p] !== taslak[p]);

  const kaydet = async () => {
    setKaydediliyor(true);
    setHata(null);
    try {
      kaydedildi(await api.parcalar(aracId, Object.fromEntries(degisti.map((p) => [p, taslak[p]]))));
    } catch (e) {
      setHata(hataMesaji(e));
    } finally {
      setKaydediliyor(false);
    }
  };

  return (
    <div>
      <HasarSemasi parcalar={taslak} duzenlenebilir={duzenlenebilir} onDegis={(p, d) => setTaslak((t) => ({ ...t, [p]: d }))} />
      {degisti.length > 0 && (
        <div className="mt-4 flex items-center justify-between gap-2 rounded-lg bg-plaka-acik px-3 py-2">
          <span className="text-[14px] font-semibold text-plaka-koyu">{degisti.length} parça değişti</span>
          <div className="flex gap-2">
            <Buton tur="sessiz" className="h-9" onClick={() => setTaslak(parcalar)}>Geri al</Buton>
            <Buton className="h-9" onClick={kaydet} disabled={kaydediliyor}>Kaydet</Buton>
          </div>
        </div>
      )}
      <div className="mt-2"><HataKutusu mesaj={hata} /></div>
    </div>
  );
}

function Kart({ baslik, children }: { baslik: string; children: ReactNode }) {
  return (
    <section className="bg-yuzey rounded-xl border border-cizgi p-4 md:p-5">
      <h2 className="font-cond font-semibold text-[20px] mb-3">{baslik}</h2>
      {children}
    </section>
  );
}

function Bilgi({ etiket, deger, sayi: sayiMi = false, vurgu = false }: { etiket: string; deger: string; sayi?: boolean; vurgu?: boolean }) {
  return (
    <div>
      <dt className="text-[13px] text-murekkep-2">{etiket}</dt>
      <dd className={`${sayiMi ? "font-cond text-[19px]" : "text-[16px]"} ${vurgu ? "font-bold" : "font-medium"}`}>{deger}</dd>
    </div>
  );
}
