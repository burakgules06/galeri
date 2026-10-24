import { useState } from "react";
import { api } from "../../api/galeri";
import { useAuth } from "../../auth/useAuth";
import { git } from "../../shared/useRota";
import { useYukle } from "../../shared/useYukle";
import { sayi, VITES, YAKIT } from "../../shared/format";
import type { AracDurum, AracOzet } from "../../shared/types";
import { Plaka } from "../../shared/ui/Plaka";
import { Bos, Buton, girdiSinifi, HataKutusu, Segment } from "../../shared/ui/Temel";
import { HasarRozeti } from "./HasarSemasi";
import { StoktaGun } from "./StoktaGun";
import { AracFormu } from "./AracFormu";

type Filtre = "STOKTA" | "SATILDI" | "HEPSI";

export function AracListesi() {
  const { isAdmin } = useAuth();
  const [q, setQ] = useState("");
  const [filtre, setFiltre] = useState<Filtre>("STOKTA");
  const [formAcik, setFormAcik] = useState(false);
  const durum: AracDurum | null = filtre === "HEPSI" ? null : filtre;
  const { veri, yukleniyor, hata } = useYukle(() => api.araclar(q, durum), [q, durum]);
  const liste = veri ?? [];

  return (
    <div>
      <div className="flex items-end justify-between gap-3 mb-4">
        <div>
          <h1 className="font-cond font-bold text-[30px] leading-none tracking-tight">Araçlar</h1>
          <p className="text-murekkep-2 mt-1">{veri ? `${liste.length} araç` : " "}</p>
        </div>
        {isAdmin && <Buton onClick={() => setFormAcik(true)}>Araç ekle</Buton>}
      </div>

      <div className="flex flex-col md:flex-row gap-2 md:items-center mb-4">
        <input
          className={`${girdiSinifi} md:max-w-xs`}
          placeholder="Plaka, marka ya da model ara"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          aria-label="Araç ara"
        />
        <Segment<Filtre>
          deger={filtre}
          onChange={setFiltre}
          secenekler={[{ deger: "STOKTA", etiket: "Stokta" }, { deger: "SATILDI", etiket: "Satılanlar" }, { deger: "HEPSI", etiket: "Hepsi" }]}
        />
      </div>

      <HataKutusu mesaj={hata} />

      {veri && liste.length === 0 && !yukleniyor && (
        <Bos
          baslik={q ? "Aramaya uyan araç yok" : filtre === "STOKTA" ? "Stokta araç yok" : "Kayıt yok"}
          aciklama={isAdmin && !q ? "Galeriye aldığın ilk aracı ekleyerek başla." : undefined}
          eylem={isAdmin && !q ? <Buton onClick={() => setFormAcik(true)}>Araç ekle</Buton> : undefined}
        />
      )}

      {liste.length > 0 && (
        <>
          {/* Telefon: satir listesi */}
          <ul className="md:hidden bg-yuzey rounded-xl border border-cizgi divide-y divide-cizgi">
            {liste.map((a) => <TelefonSatiri key={a.id} a={a} />)}
          </ul>

          {/* Genis ekran: sik satirli tablo */}
          <div className="hidden md:block bg-yuzey rounded-xl border border-cizgi overflow-hidden">
            <table className="w-full text-[15px]">
              <thead className="bg-yuzey-2 text-murekkep-2 text-[13px] text-left">
                <tr>
                  <th className="font-semibold px-4 py-2.5">Plaka</th>
                  <th className="font-semibold px-3 py-2.5">Araç</th>
                  <th className="font-semibold px-3 py-2.5 text-right">Km</th>
                  <th className="font-semibold px-3 py-2.5">Yakıt, vites</th>
                  <th className="font-semibold px-3 py-2.5">Hasar</th>
                  <th className="font-semibold px-4 py-2.5 text-right">Stokta</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-cizgi">
                {liste.map((a) => (
                  <tr key={a.id} onClick={() => git(`/araclar/${a.id}`)} className="cursor-pointer hover:bg-yuzey-2">
                    <td className="px-4 py-2.5"><Plaka plaka={a.plaka} boyut="sm" /></td>
                    <td className="px-3 py-2.5">
                      <a href={`#/araclar/${a.id}`} className="font-semibold hover:underline" onClick={(e) => e.stopPropagation()}>
                        {a.marka} {a.model}
                      </a>
                      <span className="text-murekkep-2"> {a.modelYili}</span>
                    </td>
                    <td className="px-3 py-2.5 text-right font-cond text-[17px]">{sayi(a.km)}</td>
                    <td className="px-3 py-2.5 text-murekkep-2">{YAKIT[a.yakit]}, {VITES[a.vites].toLocaleLowerCase("tr-TR")}</td>
                    <td className="px-3 py-2.5"><HasarRozeti {...a.hasar} /></td>
                    <td className="px-4 py-2.5 text-right"><StoktaGun gun={a.stoktaGun} durum={a.durum} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {isAdmin && (
        <AracFormu
          acik={formAcik}
          kapat={() => setFormAcik(false)}
          kaydedildi={(d) => { setFormAcik(false); git(`/araclar/${d.arac.id}`); }}
        />
      )}
    </div>
  );
}

function TelefonSatiri({ a }: { a: AracOzet }) {
  return (
    <li>
      <a href={`#/araclar/${a.id}`} className="flex items-center gap-3 px-3.5 py-3 active:bg-yuzey-2">
        <div className="min-w-0 grow">
          <div className="flex items-center gap-2">
            <Plaka plaka={a.plaka} boyut="sm" />
          </div>
          <p className="font-semibold text-[16px] mt-1.5 truncate">{a.marka} {a.model}</p>
          <p className="text-[14px] text-murekkep-2 flex items-center gap-2 flex-wrap">
            <span>{a.modelYili}</span>
            <span className="font-cond text-[16px]">{sayi(a.km)} km</span>
            <HasarRozeti {...a.hasar} />
          </p>
        </div>
        <StoktaGun gun={a.stoktaGun} durum={a.durum} />
      </a>
    </li>
  );
}
