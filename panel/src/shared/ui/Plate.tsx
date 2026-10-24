// Turk plakasi: uygulamanin imza ogesi. Arac her yerde plakasiyla tanitilir.
type Size = "sm" | "md" | "lg";

const SIZES: Record<Size, { text: string; strip: string; tr: string; padding: string }> = {
  sm: { text: "text-[15px]", strip: "w-[14px]", tr: "text-[6px]", padding: "pl-1.5 pr-2 h-[26px]" },
  md: { text: "text-[19px]", strip: "w-[17px]", tr: "text-[7px]", padding: "pl-2 pr-2.5 h-[32px]" },
  lg: { text: "text-[30px]", strip: "w-[24px]", tr: "text-[10px]", padding: "pl-3 pr-4 h-[50px]" },
};

export function Plate({ plate, size = "md" }: { plate: string; size?: Size }) {
  const s = SIZES[size];
  return (
    <span
      className="inline-flex items-stretch rounded-[5px] border-[1.5px] border-murekkep bg-white overflow-hidden shrink-0 align-middle"
      aria-label={`Plaka ${plate}`}
    >
      <span className={`${s.strip} bg-plaka flex items-end justify-center pb-[2px]`}>
        <span className={`${s.tr} font-cond font-semibold text-white leading-none`}>TR</span>
      </span>
      <span className={`${s.padding} ${s.text} flex items-center font-cond font-bold tracking-[0.06em] leading-none whitespace-nowrap`}>
        {plate}
      </span>
    </span>
  );
}
