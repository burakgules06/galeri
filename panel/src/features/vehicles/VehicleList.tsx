import { useState } from "react";
import { api } from "../../api/galeri";
import { useAuth } from "../../auth/useAuth";
import { navigate } from "../../shared/useRoute";
import { useLoad } from "../../shared/useLoad";
import { formatNumber, FUEL, TRANSMISSION } from "../../shared/format";
import type { VehicleStatus, VehicleSummary } from "../../shared/types";
import { Plate } from "../../shared/ui/Plate";
import { Button, Empty, ErrorBox, inputClass, Segment } from "../../shared/ui/Common";
import { DamageBadge } from "./DamageMap";
import { DaysInStock } from "./DaysInStock";
import { VehicleForm } from "./VehicleForm";

type Filter = "IN_STOCK" | "SOLD" | "ALL";

export function VehicleList() {
  const { isAdmin } = useAuth();
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<Filter>("IN_STOCK");
  const [formOpen, setFormOpen] = useState(false);
  const status: VehicleStatus | null = filter === "ALL" ? null : filter;
  const { data, loading, error } = useLoad(() => api.vehicles(q, status), [q, status]);
  const list = data ?? [];

  return (
    <div>
      <div className="flex items-end justify-between gap-3 mb-4">
        <div>
          <h1 className="font-cond font-bold text-[30px] leading-none tracking-tight">Araçlar</h1>
          <p className="text-murekkep-2 mt-1">{data ? `${list.length} araç` : " "}</p>
        </div>
        {isAdmin && <Button onClick={() => setFormOpen(true)}>Araç ekle</Button>}
      </div>

      <div className="flex flex-col md:flex-row gap-2 md:items-center mb-4">
        <input
          className={`${inputClass} md:max-w-xs`}
          placeholder="Plaka, marka ya da model ara"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          aria-label="Araç ara"
        />
        <Segment<Filter>
          value={filter}
          onChange={setFilter}
          options={[{ value: "IN_STOCK", label: "Stokta" }, { value: "SOLD", label: "Satılanlar" }, { value: "ALL", label: "Hepsi" }]}
        />
      </div>

      <ErrorBox message={error} />

      {data && list.length === 0 && !loading && (
        <Empty
          title={q ? "Aramaya uyan araç yok" : filter === "IN_STOCK" ? "Stokta araç yok" : "Kayıt yok"}
          description={isAdmin && !q ? "Galeriye aldığın ilk aracı ekleyerek başla." : undefined}
          action={isAdmin && !q ? <Button onClick={() => setFormOpen(true)}>Araç ekle</Button> : undefined}
        />
      )}

      {list.length > 0 && (
        <>
          {/* Telefon: satir listesi */}
          <ul className="md:hidden bg-yuzey rounded-xl border border-cizgi divide-y divide-cizgi">
            {list.map((v) => <PhoneRow key={v.id} v={v} />)}
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
                {list.map((v) => (
                  <tr key={v.id} onClick={() => navigate(`/vehicles/${v.id}`)} className="cursor-pointer hover:bg-yuzey-2">
                    <td className="px-4 py-2.5"><Plate plate={v.plate} size="sm" /></td>
                    <td className="px-3 py-2.5">
                      <a href={`#/vehicles/${v.id}`} className="font-semibold hover:underline" onClick={(e) => e.stopPropagation()}>
                        {v.make} {v.model}
                      </a>
                      <span className="text-murekkep-2"> {v.modelYear}</span>
                    </td>
                    <td className="px-3 py-2.5 text-right font-cond text-[17px]">{formatNumber(v.mileageKm)}</td>
                    <td className="px-3 py-2.5 text-murekkep-2">{FUEL[v.fuel]}, {TRANSMISSION[v.transmission].toLocaleLowerCase("tr-TR")}</td>
                    <td className="px-3 py-2.5"><DamageBadge {...v.damage} /></td>
                    <td className="px-4 py-2.5 text-right"><DaysInStock days={v.daysInStock} status={v.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {isAdmin && (
        <VehicleForm
          open={formOpen}
          onClose={() => setFormOpen(false)}
          onSaved={(d) => { setFormOpen(false); navigate(`/vehicles/${d.vehicle.id}`); }}
        />
      )}
    </div>
  );
}

function PhoneRow({ v }: { v: VehicleSummary }) {
  return (
    <li>
      <a href={`#/vehicles/${v.id}`} className="flex items-center gap-3 px-3.5 py-3 active:bg-yuzey-2">
        <div className="min-w-0 grow">
          <div className="flex items-center gap-2">
            <Plate plate={v.plate} size="sm" />
          </div>
          <p className="font-semibold text-[16px] mt-1.5 truncate">{v.make} {v.model}</p>
          <p className="text-[14px] text-murekkep-2 flex items-center gap-2 flex-wrap">
            <span>{v.modelYear}</span>
            <span className="font-cond text-[16px]">{formatNumber(v.mileageKm)} km</span>
            <DamageBadge {...v.damage} />
          </p>
        </div>
        <DaysInStock days={v.daysInStock} status={v.status} />
      </a>
    </li>
  );
}
