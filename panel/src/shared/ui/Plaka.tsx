// Turk plakasi: uygulamanin imza ogesi. Arac her yerde plakasiyla tanitilir.
type Boyut = "sm" | "md" | "lg";

const OLCU: Record<Boyut, { yazi: string; serit: string; tr: string; dolgu: string }> = {
  sm: { yazi: "text-[15px]", serit: "w-[14px]", tr: "text-[6px]", dolgu: "pl-1.5 pr-2 h-[26px]" },
  md: { yazi: "text-[19px]", serit: "w-[17px]", tr: "text-[7px]", dolgu: "pl-2 pr-2.5 h-[32px]" },
  lg: { yazi: "text-[30px]", serit: "w-[24px]", tr: "text-[10px]", dolgu: "pl-3 pr-4 h-[50px]" },
};

export function Plaka({ plaka, boyut = "md" }: { plaka: string; boyut?: Boyut }) {
  const o = OLCU[boyut];
  return (
    <span
      className="inline-flex items-stretch rounded-[5px] border-[1.5px] border-murekkep bg-white overflow-hidden shrink-0 align-middle"
      aria-label={`Plaka ${plaka}`}
    >
      <span className={`${o.serit} bg-plaka flex items-end justify-center pb-[2px]`}>
        <span className={`${o.tr} font-cond font-semibold text-white leading-none`}>TR</span>
      </span>
      <span className={`${o.dolgu} ${o.yazi} flex items-center font-cond font-bold tracking-[0.06em] leading-none whitespace-nowrap`}>
        {plaka}
      </span>
    </span>
  );
}
