// Ay secici: onceki / sonraki ay ve "Tüm zamanlar".
import { todayIso } from "../../shared/format";
import { currentPeriod, periodLabel, shiftPeriod, type Period } from "./period";

export function PeriodPicker({ value, onChange }: { value: Period; onChange: (p: Period) => void }) {
  const all = value.month == null;
  const arrow = "basilir w-9 h-9 rounded-lg grid place-items-center text-murekkep-2 hover:bg-yuzey-2 disabled:opacity-30";
  return (
    <div className="flex items-center gap-1 rounded-[12px] border border-cizgi bg-yuzey p-1">
      <button type="button" className={arrow} disabled={all} aria-label="Önceki ay" onClick={() => onChange(shiftPeriod(value, -1))}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M15 6l-6 6 6 6" /></svg>
      </button>
      <span className="min-w-[124px] text-center font-semibold text-[14px]">{periodLabel(value)}</span>
      <button type="button" className={arrow} disabled={all} aria-label="Sonraki ay" onClick={() => onChange(shiftPeriod(value, 1))}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M9 6l6 6-6 6" /></svg>
      </button>
      <button type="button" onClick={() => onChange(all ? currentPeriod(todayIso()) : { year: value.year, month: null })}
        className="basilir h-9 px-2.5 rounded-lg text-[13px] font-semibold text-plaka hover:bg-plaka-acik">
        {all ? "Bu ay" : "Tümü"}
      </button>
    </div>
  );
}
