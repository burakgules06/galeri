// Stokta bekleme suresi: bayi yazilimlarinin en cok baktigi sayi. 30+ gun uyari, 60+ gun tehlike.
import type { AracDurum } from "../../shared/types";

export function StoktaGun({ gun, durum, buyuk = false }: { gun: number; durum: AracDurum; buyuk?: boolean }) {
  if (durum !== "STOKTA") {
    return <span className="text-murekkep-3 text-[14px]">{gun} günde satıldı</span>;
  }
  const renk = gun >= 60 ? "bg-tehlike-zemin text-tehlike" : gun >= 30 ? "bg-uyari-zemin text-uyari" : "bg-yuzey-2 text-murekkep-2";
  return (
    <span className={`inline-flex items-baseline gap-1 rounded-md px-2 ${buyuk ? "py-1" : "py-0.5"} ${renk}`}>
      <span className={`font-cond font-bold ${buyuk ? "text-[22px]" : "text-[17px]"} leading-none`}>{gun}</span>
      <span className="text-[13px] font-medium">gün</span>
    </span>
  );
}
