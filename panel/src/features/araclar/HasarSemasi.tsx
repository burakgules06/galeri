// Sahibinden / arabam.com tarzi ustten arac cizimi. 13 parca, her biri 4 durumdan biri.
// Duzenleme: once alttaki "firca" durumunu sec, sonra parcalara dokun. Ayni durumdaki parcaya
// tekrar dokunmak onu orijinale dondurur.
import { useState } from "react";
import type { Parca, ParcaDurum } from "../../shared/types";
import { PARCA, PARCA_DURUM } from "../../shared/format";

const DOLGU: Record<ParcaDurum, string> = {
  ORIJINAL: "var(--color-orijinal)",
  LOKAL_BOYALI: "var(--color-lokal)",
  BOYALI: "var(--color-boyali)",
  DEGISEN: "var(--color-degisen)",
};

const DURUMLAR: ParcaDurum[] = ["ORIJINAL", "LOKAL_BOYALI", "BOYALI", "DEGISEN"];

// viewBox 0 0 220 400, on taraf yukarida. Sol taraf aracin sol yani.
const SEKIL: Record<Parca, string> = {
  ON_TAMPON: "M58 14 Q110 2 162 14 L166 34 L54 34 Z",
  MOTOR_KAPUTU: "M54 38 L166 38 L162 118 L58 118 Z",
  TAVAN: "M64 176 L156 176 L156 268 L64 268 Z",
  BAGAJ_KAPAGI: "M58 302 L162 302 L166 352 L54 352 Z",
  ARKA_TAMPON: "M54 356 L166 356 L162 380 Q110 392 58 380 Z",
  SOL_ON_CAMURLUK: "M22 54 Q24 38 50 36 L52 120 L20 120 Z",
  SOL_ON_KAPI: "M20 124 L52 124 L52 210 L20 210 Z",
  SOL_ARKA_KAPI: "M20 214 L52 214 L52 296 L20 296 Z",
  SOL_ARKA_CAMURLUK: "M20 300 L52 300 L50 358 Q24 356 22 342 Z",
  SAG_ON_CAMURLUK: "M198 54 Q196 38 170 36 L168 120 L200 120 Z",
  SAG_ON_KAPI: "M168 124 L200 124 L200 210 L168 210 Z",
  SAG_ARKA_KAPI: "M168 214 L200 214 L200 296 L168 296 Z",
  SAG_ARKA_CAMURLUK: "M168 300 L200 300 L198 342 Q196 356 170 358 Z",
};

const CAM = [
  "M60 124 L160 124 L154 170 L66 170 Z", // on cam
  "M66 274 L154 274 L160 296 L60 296 Z", // arka cam
];

export function HasarSemasi({ parcalar, duzenlenebilir = false, onDegis }: {
  parcalar: Record<Parca, ParcaDurum>;
  duzenlenebilir?: boolean;
  onDegis?: (parca: Parca, durum: ParcaDurum) => void;
}) {
  const [firca, setFirca] = useState<ParcaDurum>("BOYALI");

  const dokun = (p: Parca) => {
    if (!duzenlenebilir || !onDegis) return;
    onDegis(p, parcalar[p] === firca ? "ORIJINAL" : firca);
  };

  const sayilar = DURUMLAR.map((d) => ({ d, n: Object.values(parcalar).filter((x) => x === d).length }));
  const hasarli = (Object.keys(SEKIL) as Parca[]).filter((p) => parcalar[p] !== "ORIJINAL");

  return (
    <div>
      <div className="flex justify-center">
        <svg viewBox="0 -16 220 416" className="w-full max-w-[240px] h-auto select-none" role="group" aria-label="Hasar şeması">
          <text x="110" y="-4" textAnchor="middle" className="fill-murekkep-3" fontSize="11">Ön</text>
          <text x="6" y="210" className="fill-murekkep-3" fontSize="11" transform="rotate(-90 6 210)" textAnchor="middle">Sol</text>
          <text x="214" y="210" className="fill-murekkep-3" fontSize="11" transform="rotate(90 214 210)" textAnchor="middle">Sağ</text>
          {CAM.map((d) => <path key={d} d={d} fill="var(--color-plaka-acik)" stroke="white" strokeWidth="2" />)}
          {(Object.keys(SEKIL) as Parca[]).map((p) => (
            <path
              key={p}
              d={SEKIL[p]}
              fill={DOLGU[parcalar[p]]}
              stroke="white"
              strokeWidth="2.5"
              strokeLinejoin="round"
              role={duzenlenebilir ? "button" : undefined}
              tabIndex={duzenlenebilir ? 0 : undefined}
              aria-label={`${PARCA[p]}: ${PARCA_DURUM[parcalar[p]]}`}
              onClick={() => dokun(p)}
              onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); dokun(p); } }}
              className={duzenlenebilir ? "cursor-pointer hover:opacity-80 focus:outline-none" : undefined}
            >
              <title>{`${PARCA[p]}: ${PARCA_DURUM[parcalar[p]]}`}</title>
            </path>
          ))}
        </svg>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-2">
        {sayilar.map(({ d, n }) => {
          const secili = duzenlenebilir && firca === d;
          return (
            <button
              key={d}
              type="button"
              disabled={!duzenlenebilir}
              onClick={() => setFirca(d)}
              aria-pressed={secili}
              className={`basilir flex items-center gap-2 h-10 px-3 rounded-lg border text-[14px] font-semibold text-left disabled:cursor-default ${
                secili ? "border-murekkep bg-yuzey-2" : "border-cizgi bg-yuzey"
              }`}
            >
              <span className="w-4 h-4 rounded-[4px] shrink-0" style={{ background: DOLGU[d] }} />
              <span className="grow">{PARCA_DURUM[d]}</span>
              <span className="font-cond text-[17px] text-murekkep-2">{n}</span>
            </button>
          );
        })}
      </div>
      {duzenlenebilir && (
        <p className="text-[13px] text-murekkep-3 mt-2">Durumu seç, sonra çizimde parçalara dokun. Aynı durumdaki parçaya tekrar dokunmak orijinale çevirir.</p>
      )}

      {hasarli.length > 0 && (
        <ul className="mt-4 divide-y divide-cizgi border-t border-cizgi">
          {hasarli.map((p) => (
            <li key={p} className="flex items-center justify-between py-2 text-[15px]">
              <span>{PARCA[p]}</span>
              <span className="flex items-center gap-2 text-murekkep-2">
                <span className="w-2.5 h-2.5 rounded-full" style={{ background: DOLGU[parcalar[p]] }} />
                {PARCA_DURUM[parcalar[p]]}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Listede kucuk ozet: renkli noktalar + sayi. Hasarsizsa "Hatasız". */
export function HasarRozeti({ lokalBoyali, boyali, degisen }: { lokalBoyali: number; boyali: number; degisen: number }) {
  if (!lokalBoyali && !boyali && !degisen) {
    return <span className="text-[14px] text-basari font-semibold">Hatasız</span>;
  }
  const parca = (n: number, d: ParcaDurum, etiket: string) =>
    n > 0 && (
      <span className="inline-flex items-center gap-1" title={`${n} ${etiket}`}>
        <span className="w-2.5 h-2.5 rounded-full" style={{ background: DOLGU[d] }} />
        <span className="font-cond font-semibold text-[16px]">{n}</span>
      </span>
    );
  return (
    <span className="inline-flex items-center gap-2.5" aria-label={`${lokalBoyali} lokal boyalı, ${boyali} boyalı, ${degisen} değişen`}>
      {parca(lokalBoyali, "LOKAL_BOYALI", "lokal boyalı")}
      {parca(boyali, "BOYALI", "boyalı")}
      {parca(degisen, "DEGISEN", "değişen")}
    </span>
  );
}
