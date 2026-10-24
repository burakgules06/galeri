// Sahibinden / arabam.com tarzi ustten arac cizimi. 13 parca, her biri 4 durumdan biri.
// Duzenleme: once alttaki "firca" durumunu sec, sonra parcalara dokun. Ayni durumdaki parcaya
// tekrar dokunmak onu orijinale dondurur.
import { useState } from "react";
import type { BodyPart, PartCondition } from "../../shared/types";
import { BODY_PART, PART_CONDITION } from "../../shared/format";

const FILL: Record<PartCondition, string> = {
  ORIGINAL: "var(--color-orijinal)",
  LOCALLY_PAINTED: "var(--color-lokal)",
  PAINTED: "var(--color-boyali)",
  REPLACED: "var(--color-degisen)",
};

const CONDITIONS: PartCondition[] = ["ORIGINAL", "LOCALLY_PAINTED", "PAINTED", "REPLACED"];

// viewBox 0 0 220 400, on taraf yukarida. Sol taraf aracin sol yani.
const SHAPE: Record<BodyPart, string> = {
  FRONT_BUMPER: "M58 14 Q110 2 162 14 L166 34 L54 34 Z",
  HOOD: "M54 38 L166 38 L162 118 L58 118 Z",
  ROOF: "M64 176 L156 176 L156 268 L64 268 Z",
  TRUNK_LID: "M58 302 L162 302 L166 352 L54 352 Z",
  REAR_BUMPER: "M54 356 L166 356 L162 380 Q110 392 58 380 Z",
  LEFT_FRONT_FENDER: "M22 54 Q24 38 50 36 L52 120 L20 120 Z",
  LEFT_FRONT_DOOR: "M20 124 L52 124 L52 210 L20 210 Z",
  LEFT_REAR_DOOR: "M20 214 L52 214 L52 296 L20 296 Z",
  LEFT_REAR_FENDER: "M20 300 L52 300 L50 358 Q24 356 22 342 Z",
  RIGHT_FRONT_FENDER: "M198 54 Q196 38 170 36 L168 120 L200 120 Z",
  RIGHT_FRONT_DOOR: "M168 124 L200 124 L200 210 L168 210 Z",
  RIGHT_REAR_DOOR: "M168 214 L200 214 L200 296 L168 296 Z",
  RIGHT_REAR_FENDER: "M168 300 L200 300 L198 342 Q196 356 170 358 Z",
};

const GLASS = [
  "M60 124 L160 124 L154 170 L66 170 Z", // on cam
  "M66 274 L154 274 L160 296 L60 296 Z", // arka cam
];

export function DamageMap({ parts, editable = false, onChange }: {
  parts: Record<BodyPart, PartCondition>;
  editable?: boolean;
  onChange?: (part: BodyPart, condition: PartCondition) => void;
}) {
  const [brush, setBrush] = useState<PartCondition>("PAINTED");

  const tap = (p: BodyPart) => {
    if (!editable || !onChange) return;
    onChange(p, parts[p] === brush ? "ORIGINAL" : brush);
  };

  const counts = CONDITIONS.map((c) => ({ c, n: Object.values(parts).filter((x) => x === c).length }));
  const damaged = (Object.keys(SHAPE) as BodyPart[]).filter((p) => parts[p] !== "ORIGINAL");

  return (
    <div>
      <div className="flex justify-center">
        <svg viewBox="0 -16 220 416" className="w-full max-w-[240px] h-auto select-none" role="group" aria-label="Hasar şeması">
          <text x="110" y="-4" textAnchor="middle" className="fill-murekkep-3" fontSize="11">Ön</text>
          <text x="6" y="210" className="fill-murekkep-3" fontSize="11" transform="rotate(-90 6 210)" textAnchor="middle">Sol</text>
          <text x="214" y="210" className="fill-murekkep-3" fontSize="11" transform="rotate(90 214 210)" textAnchor="middle">Sağ</text>
          {GLASS.map((d) => <path key={d} d={d} fill="var(--color-plaka-acik)" stroke="white" strokeWidth="2" />)}
          {(Object.keys(SHAPE) as BodyPart[]).map((p) => (
            <path
              key={p}
              d={SHAPE[p]}
              fill={FILL[parts[p]]}
              stroke="white"
              strokeWidth="2.5"
              strokeLinejoin="round"
              role={editable ? "button" : undefined}
              tabIndex={editable ? 0 : undefined}
              aria-label={`${BODY_PART[p]}: ${PART_CONDITION[parts[p]]}`}
              onClick={() => tap(p)}
              onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); tap(p); } }}
              className={editable ? "cursor-pointer hover:opacity-80 focus:outline-none" : undefined}
            >
              <title>{`${BODY_PART[p]}: ${PART_CONDITION[parts[p]]}`}</title>
            </path>
          ))}
        </svg>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-2">
        {counts.map(({ c, n }) => {
          const selected = editable && brush === c;
          return (
            <button
              key={c}
              type="button"
              disabled={!editable}
              onClick={() => setBrush(c)}
              aria-pressed={selected}
              className={`basilir flex items-center gap-2 h-10 px-3 rounded-lg border text-[14px] font-semibold text-left disabled:cursor-default ${
                selected ? "border-murekkep bg-yuzey-2" : "border-cizgi bg-yuzey"
              }`}
            >
              <span className="w-4 h-4 rounded-[4px] shrink-0" style={{ background: FILL[c] }} />
              <span className="grow">{PART_CONDITION[c]}</span>
              <span className="font-cond text-[17px] text-murekkep-2">{n}</span>
            </button>
          );
        })}
      </div>
      {editable && (
        <p className="text-[13px] text-murekkep-3 mt-2">Durumu seç, sonra çizimde parçalara dokun. Aynı durumdaki parçaya tekrar dokunmak orijinale çevirir.</p>
      )}

      {damaged.length > 0 && (
        <ul className="mt-4 divide-y divide-cizgi border-t border-cizgi">
          {damaged.map((p) => (
            <li key={p} className="flex items-center justify-between py-2 text-[15px]">
              <span>{BODY_PART[p]}</span>
              <span className="flex items-center gap-2 text-murekkep-2">
                <span className="w-2.5 h-2.5 rounded-full" style={{ background: FILL[parts[p]] }} />
                {PART_CONDITION[parts[p]]}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Listede kucuk ozet: renkli noktalar + sayi. Hasarsizsa "Hatasız". */
export function DamageBadge({ locallyPainted, painted, replaced }: { locallyPainted: number; painted: number; replaced: number }) {
  if (!locallyPainted && !painted && !replaced) {
    return <span className="text-[14px] text-basari font-semibold">Hatasız</span>;
  }
  const dot = (n: number, c: PartCondition, label: string) =>
    n > 0 && (
      <span className="inline-flex items-center gap-1" title={`${n} ${label}`}>
        <span className="w-2.5 h-2.5 rounded-full" style={{ background: FILL[c] }} />
        <span className="font-cond font-semibold text-[16px]">{n}</span>
      </span>
    );
  return (
    <span className="inline-flex items-center gap-2.5" aria-label={`${locallyPainted} lokal boyalı, ${painted} boyalı, ${replaced} değişen`}>
      {dot(locallyPainted, "LOCALLY_PAINTED", "lokal boyalı")}
      {dot(painted, "PAINTED", "boyalı")}
      {dot(replaced, "REPLACED", "değişen")}
    </span>
  );
}
