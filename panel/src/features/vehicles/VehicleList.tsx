import { useState } from "react";
import { api } from "../../api/galeri";
import { useAuth } from "../../auth/useAuth";
import { navigate } from "../../shared/useRoute";
import { useLoad } from "../../shared/useLoad";
import { formatNumber, formatPhone, FUEL, TRANSMISSION } from "../../shared/format";
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
  const showBuyer = isAdmin && filter !== "IN_STOCK";

  return (
    <div>
      <div className="flex items-end justify-between gap-3 mb-4">
        <div>
          <p className="etiket">Stok</p>
          <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">
            Araçlar {data && <span className="sayi font-semibold text-murekkep-3">{list.length}</span>}
          </h1>
        </div>
        {isAdmin && <Button onClick={() => setFormOpen(true)}>Araç ekle</Button>}
      </div>

      <div className="flex flex-col md:flex-row gap-2 md:items-center mb-4">
        <div className="relative md:w-[360px]">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 text-murekkep-3" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"><circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" /></svg>
          <input
            className={`${inputClass} pl-9`}
            placeholder={isAdmin ? "Plaka, marka, model, alıcı adı ya da telefon" : "Plaka, marka ya da model ara"}
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Araç ara"
          />
        </div>
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
          <ul className="md:hidden kart overflow-hidden divide-y divide-cizgi">
            {list.map((v) => <PhoneRow key={v.id} v={v} showBuyer={showBuyer} />)}
          </ul>

          {/* Genis ekran: sik satirli tablo */}
          <div className="hidden md:block kart overflow-hidden">
            <table className="w-full text-[15px]">
              <thead className="bg-yuzey-2 text-murekkep-3 text-[11.5px] uppercase tracking-[0.08em] text-left border-b border-cizgi">
                <tr>
                  <th className="font-semibold px-4 py-2.5">Plaka</th>
                  <th className="font-semibold px-3 py-2.5">Araç</th>
                  <th className="font-semibold px-3 py-2.5 text-right">Km</th>
                  <th className="font-semibold px-3 py-2.5">Yakıt, vites</th>
                  <th className="font-semibold px-3 py-2.5">Hasar</th>
                  {showBuyer && <th className="font-semibold px-3 py-2.5">Alıcı</th>}
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
                    {showBuyer && <td className="px-3 py-2.5"><Buyer v={v} /></td>}
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

function Buyer({ v }: { v: VehicleSummary }) {
  if (!v.buyer) return <span className="text-murekkep-3">—</span>;
  return (
    <span className="block leading-tight">
      <span className="block font-semibold text-[14px] truncate">{v.buyer.name}</span>
      {v.buyer.phone && <span className="block sayi text-[13.5px] text-murekkep-2">{formatPhone(v.buyer.phone)}</span>}
    </span>
  );
}

function PhoneRow({ v, showBuyer }: { v: VehicleSummary; showBuyer: boolean }) {
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
          {showBuyer && v.buyer && (
            <p className="text-[13px] text-murekkep-2 mt-1 truncate">
              Alıcı: <span className="font-semibold text-murekkep">{v.buyer.name}</span>
              {v.buyer.phone && <span className="sayi text-[14px]"> · {formatPhone(v.buyer.phone)}</span>}
            </p>
          )}
        </div>
        <DaysInStock days={v.daysInStock} status={v.status} />
      </a>
    </li>
  );
}
