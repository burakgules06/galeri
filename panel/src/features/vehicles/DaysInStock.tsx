// Stokta bekleme suresi: bayi yazilimlarinin en cok baktigi sayi. 30+ gun uyari, 60+ gun tehlike.
import type { VehicleStatus } from "../../shared/types";

export function DaysInStock({ days, status, large = false }: { days: number; status: VehicleStatus; large?: boolean }) {
  if (status !== "IN_STOCK") {
    return <span className="text-murekkep-3 text-[14px]">{days} günde satıldı</span>;
  }
  const color = days >= 60 ? "bg-tehlike-zemin text-tehlike" : days >= 30 ? "bg-uyari-zemin text-uyari" : "bg-yuzey-2 text-murekkep-2";
  return (
    <span className={`inline-flex items-baseline gap-1 rounded-md px-2 ${large ? "py-1" : "py-0.5"} ${color}`}>
      <span className={`font-cond font-bold ${large ? "text-[22px]" : "text-[17px]"} leading-none`}>{days}</span>
      <span className="text-[13px] font-medium">gün</span>
    </span>
  );
}
