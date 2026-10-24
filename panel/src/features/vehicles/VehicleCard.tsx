// Ana ekrandaki arac karti. Ustteki ince serit stok yasini soyler: gri < 30 gun, turuncu 30-59, kirmizi 60+.
import { formatNumber, FUEL, TRANSMISSION } from "../../shared/format";
import type { VehicleSummary } from "../../shared/types";
import { Plate } from "../../shared/ui/Plate";
import { DamageBadge } from "./DamageMap";
import { DaysInStock } from "./DaysInStock";

export function VehicleCard({ v }: { v: VehicleSummary }) {
  const age = v.status !== "IN_STOCK" ? "bg-cizgi" : v.daysInStock >= 60 ? "bg-tehlike-canli" : v.daysInStock >= 30 ? "bg-boyali" : "bg-cizgi-2";
  return (
    <a href={`#/vehicles/${v.id}`}
      className="kart basilir group block overflow-hidden hover:border-cizgi-2 hover:shadow-md transition-shadow">
      <span className={`block h-[3px] ${age}`} />
      <div className="p-4">
        <div className="flex items-start justify-between gap-3">
          <Plate plate={v.plate} size="sm" />
          <DaysInStock days={v.daysInStock} status={v.status} />
        </div>
        <p className="mt-3 font-semibold text-[17px] leading-snug truncate group-hover:text-plaka">{v.make} <span className="font-medium">{v.model}</span></p>
        <p className="mt-0.5 text-[13.5px] text-murekkep-2 truncate">
          <span className="font-semibold text-murekkep">{v.modelYear}</span>
          <span className="mx-1.5 text-murekkep-3">·</span>
          <span className="sayi text-[15px]">{formatNumber(v.mileageKm)}</span> km
          <span className="mx-1.5 text-murekkep-3">·</span>
          {FUEL[v.fuel]}, {TRANSMISSION[v.transmission].toLocaleLowerCase("tr-TR")}
        </p>
        <div className="mt-3 pt-3 border-t border-cizgi flex items-center justify-between">
          <span className="etiket">Hasar</span>
          <DamageBadge {...v.damage} />
        </div>
      </div>
    </a>
  );
}
