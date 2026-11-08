// Musteri vitrini (girissiz): /vitrin/{slug} galerinin vitrindeki stok araclari, /vitrin/{slug}/{aracId} arac detayi.
// Alis fiyati, maliyet, satici, sahiplik ve ic notlar sunucudan hic gelmez.
import { useEffect, useState, type ReactNode } from "react";
import { api } from "../../api/galeri";
import { apiUrl } from "../../api/client";
import { BODY_PART, formatNumber, FUEL, tl, TRANSMISSION } from "../../shared/format";
import type { BodyPart, PartCondition, PublicGallery, PublicShowroomCard, PublicVehicle } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { DamageMap } from "../vehicles/DamageMap";
import { parseShowroomPath, telLink, whatsappLink } from "./showroomLinks";

function usePath() {
  const [path, setPath] = useState(window.location.pathname);
  useEffect(() => {
    const onPop = () => setPath(window.location.pathname);
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);
  const go = (to: string) => {
    window.history.pushState(null, "", to);
    setPath(to);
    window.scrollTo(0, 0);
  };
  return { path, go };
}

export function ShowroomApp() {
  const { path, go } = usePath();
  const route = parseShowroomPath(path);
  if (!route) return <Message title="Sayfa bulunamadı" />;
  return route.vehicleId
    ? <VehiclePage slug={route.slug} id={route.vehicleId} onBack={() => go(`/vitrin/${route.slug}`)} />
    : <GalleryPage slug={route.slug} onOpen={(id) => go(`/vitrin/${route.slug}/${id}`)} />;
}

function GalleryPage({ slug, onOpen }: { slug: string; onOpen: (id: string) => void }) {
  const gallery = useLoad(() => api.publicGallery(slug), [slug]);
  useEffect(() => { if (gallery.data) document.title = `${gallery.data.name} · Araçlar`; }, [gallery.data]);
  if (gallery.error) return <Message title="Galeri bulunamadı" text="Link yanlış ya da galeri artık yayında değil." />;
  if (!gallery.data) return <Shell name="" phone={null}><Loading /></Shell>;
  const g: PublicGallery = gallery.data;
  return (
    <Shell name={g.name} phone={g.phone} whatsappText="Merhaba, araçlarınız hakkında bilgi almak istiyorum.">
      <div className="flex items-baseline justify-between gap-3 mb-4">
        <h1 className="font-cond font-bold text-[28px] md:text-[34px] leading-tight tracking-tight">Satıştaki araçlar</h1>
        <span className="sayi text-[16px] text-murekkep-2 shrink-0">{g.vehicles.length} araç</span>
      </div>
      {g.vehicles.length === 0
        ? <p className="kart p-6 text-center text-murekkep-2">Şu an vitrinde araç yok. Bilgi için bize ulaşın.</p>
        : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {g.vehicles.map((v) => <VehicleTile key={v.id} v={v} onOpen={() => onOpen(v.id)} />)}
          </div>
        )}
    </Shell>
  );
}

function VehicleTile({ v, onOpen }: { v: PublicShowroomCard; onOpen: () => void }) {
  return (
    <a href={`${window.location.pathname.replace(/\/$/, "")}/${v.id}`}
      onClick={(e) => { if (e.metaKey || e.ctrlKey) return; e.preventDefault(); onOpen(); }}
      className="kart basilir block overflow-hidden hover:border-cizgi-2 focus-visible:outline-2 focus-visible:outline-plaka">
      <div className="relative aspect-[4/3] bg-zemin">
        {v.cover
          ? <img src={apiUrl(v.cover.thumbUrl)} alt={`${v.make} ${v.model}`} loading="lazy" className="w-full h-full object-cover" />
          : <div className="w-full h-full grid place-items-center text-murekkep-3 text-[14px]">Fotoğraf yok</div>}
        {v.photoCount > 1 && (
          <span className="absolute bottom-2 right-2 rounded-md bg-gece/80 text-white text-[12px] font-semibold px-2 py-0.5 sayi">{v.photoCount} foto</span>
        )}
      </div>
      <div className="p-4">
        <p className="font-cond text-[22px] leading-tight tracking-tight truncate">
          <span className="font-bold">{v.make}</span> <span className="font-medium text-murekkep-2">{v.model}</span>
        </p>
        <p className="mt-1 text-[14px] text-murekkep-2 truncate">
          <span className="sayi font-semibold text-murekkep">{v.modelYear}</span> · <span className="sayi">{formatNumber(v.mileageKm)} km</span> · {FUEL[v.fuel]} · {TRANSMISSION[v.transmission]}
        </p>
        <div className="mt-3 flex items-end justify-between gap-2">
          <Price cents={v.priceCents} />
          <DamageSummary painted={v.paintedParts} replaced={v.replacedParts} />
        </div>
      </div>
    </a>
  );
}

function VehiclePage({ slug, id, onBack }: { slug: string; id: string; onBack: () => void }) {
  const vehicle = useLoad(() => api.publicVehicle(slug, id), [slug, id]);
  useEffect(() => {
    if (vehicle.data) document.title = `${vehicle.data.make} ${vehicle.data.model} ${vehicle.data.modelYear} · ${vehicle.data.galleryName}`;
  }, [vehicle.data]);
  if (vehicle.error) {
    return <Message title="Bu araç vitrinde değil" text="Satılmış ya da yayından kaldırılmış olabilir." action={<BackLink onBack={onBack} label="Tüm araçlar" />} />;
  }
  if (!vehicle.data) return <Shell name="" phone={null}><Loading /></Shell>;
  const v: PublicVehicle = vehicle.data;
  const text = `Merhaba, vitrininizdeki ${v.make} ${v.model} ${v.modelYear} (${v.plate}) hakkında bilgi almak istiyorum.`;
  const parts = Object.fromEntries((Object.keys(BODY_PART) as BodyPart[]).map((p) => [p, "ORIGINAL"])) as Record<BodyPart, PartCondition>;
  v.parts.forEach((p) => { parts[p.part] = p.condition; });
  return (
    <Shell name={v.galleryName} phone={v.phone} whatsappText={text} sticky>
      <BackLink onBack={onBack} label="Tüm araçlar" />
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_380px] mt-3">
        <div className="min-w-0 space-y-5">
          <Photos photos={v.photos} alt={`${v.make} ${v.model}`} />
          {/* Telefonda aciklama baslik ve fiyatin altinda, genis ekranda fotograflarin altinda */}
          {v.description && <Description text={v.description} className="hidden lg:block" />}
        </div>
        <div className="space-y-5 min-w-0">
          <section className="kart p-4 md:p-5">
            <h1 className="font-cond text-[30px] leading-tight tracking-tight">
              <span className="font-bold">{v.make}</span> <span className="font-medium text-murekkep-2">{v.model}</span>
            </h1>
            <div className="mt-2"><Price cents={v.priceCents} large /></div>
            <dl className="mt-4 pt-4 border-t border-cizgi grid grid-cols-2 gap-x-4 gap-y-3">
              <Info label="Model yılı" value={String(v.modelYear)} numeric />
              <Info label="Kilometre" value={`${formatNumber(v.mileageKm)} km`} numeric />
              <Info label="Yakıt" value={FUEL[v.fuel]} />
              <Info label="Vites" value={TRANSMISSION[v.transmission]} />
              <Info label="Renk" value={v.color ?? "—"} />
              <Info label="Tramer" value={v.tramerCents ? tl(v.tramerCents) : "Yok"} numeric />
            </dl>
            {v.phone && (
              <div className="mt-5 hidden lg:grid gap-2">
                <ContactButtons phone={v.phone} text={text} />
              </div>
            )}
          </section>
          {v.description && <Description text={v.description} className="lg:hidden" />}
          <section className="kart p-4 md:p-5">
            <h2 className="etiket mb-3">Boya ve değişen</h2>
            <DamageMap parts={parts} />
          </section>
        </div>
      </div>
    </Shell>
  );
}

function Description({ text, className }: { text: string; className: string }) {
  return (
    <section className={`kart p-4 md:p-5 ${className}`}>
      <h2 className="etiket mb-2">Açıklama</h2>
      <p className="text-[16px] whitespace-pre-line">{text}</p>
    </section>
  );
}

function Photos({ photos, alt }: { photos: PublicVehicle["photos"]; alt: string }) {
  const [index, setIndex] = useState(0);
  if (photos.length === 0) {
    return <div className="kart aspect-[4/3] grid place-items-center text-murekkep-3">Fotoğraf yok</div>;
  }
  const current = photos[Math.min(index, photos.length - 1)];
  const step = (d: number) => setIndex((i) => (i + d + photos.length) % photos.length);
  return (
    <div>
      <div className="relative kart overflow-hidden bg-gece">
        <img src={apiUrl(current.url)} alt={alt} className="w-full aspect-[4/3] object-contain" />
        {photos.length > 1 && <>
          <button type="button" onClick={() => step(-1)} aria-label="Önceki fotoğraf"
            className="basilir absolute left-2 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-gece/70 text-white text-[24px] grid place-items-center">‹</button>
          <button type="button" onClick={() => step(1)} aria-label="Sonraki fotoğraf"
            className="basilir absolute right-2 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-gece/70 text-white text-[24px] grid place-items-center">›</button>
          <span className="absolute bottom-2 right-2 rounded-md bg-gece/80 text-white text-[13px] font-semibold px-2 py-0.5 sayi">{index + 1} / {photos.length}</span>
        </>}
      </div>
      {photos.length > 1 && (
        <div className="mt-2 flex gap-2 overflow-x-auto pb-1 [scrollbar-width:thin]">
          {photos.map((p, i) => (
            <button key={p.id} type="button" onClick={() => setIndex(i)} aria-label={`Fotoğraf ${i + 1}`}
              className={`shrink-0 w-20 aspect-[4/3] rounded-md overflow-hidden border-2 ${i === index ? "border-plaka" : "border-transparent opacity-80"}`}>
              <img src={apiUrl(p.thumbUrl)} alt="" loading="lazy" className="w-full h-full object-cover" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function Shell({ name, phone, whatsappText, sticky = false, children }: {
  name: string; phone: string | null; whatsappText?: string; sticky?: boolean; children: ReactNode;
}) {
  return (
    <div className={`min-h-dvh bg-zemin ${sticky && phone ? "pb-24 lg:pb-0" : ""}`}>
      <header className="bg-gece text-white">
        <div className="mx-auto max-w-[1240px] px-4 md:px-6 h-16 flex items-center justify-between gap-3">
          <p className="font-cond font-bold text-[22px] tracking-tight truncate">{name}</p>
          {phone && whatsappText && (
            <a href={whatsappLink(phone, whatsappText)} target="_blank" rel="noreferrer"
              className="basilir shrink-0 inline-flex items-center h-10 px-3 rounded-lg bg-white/10 hover:bg-white/15 text-[15px] font-semibold">
              WhatsApp
            </a>
          )}
        </div>
      </header>
      <main className="mx-auto max-w-[1240px] px-4 md:px-6 py-5 md:py-7">{children}</main>
      {sticky && phone && whatsappText && (
        <div className="lg:hidden fixed inset-x-0 bottom-0 bg-yuzey border-t border-cizgi px-4 pt-3 pb-[max(12px,env(safe-area-inset-bottom))] flex gap-2">
          <ContactButtons phone={phone} text={whatsappText} />
        </div>
      )}
    </div>
  );
}

function ContactButtons({ phone, text }: { phone: string; text: string }) {
  return <>
    <a href={whatsappLink(phone, text)} target="_blank" rel="noreferrer"
      className="basilir flex-1 inline-flex items-center justify-center h-12 rounded-lg bg-plaka hover:bg-plaka-koyu text-white text-[16px] font-semibold">
      WhatsApp'tan yaz
    </a>
    <a href={telLink(phone)}
      className="basilir flex-1 inline-flex items-center justify-center h-12 rounded-lg border border-cizgi-2 bg-yuzey text-murekkep text-[16px] font-semibold">
      Ara
    </a>
  </>;
}

function Price({ cents, large = false }: { cents: number | null; large?: boolean }) {
  return cents
    ? <p className={`sayi font-bold text-plaka ${large ? "text-[34px]" : "text-[24px]"} leading-none`}>{tl(cents)}</p>
    : <p className={`font-semibold text-murekkep-2 ${large ? "text-[18px]" : "text-[15px]"}`}>Fiyat için arayın</p>;
}

function DamageSummary({ painted, replaced }: { painted: number; replaced: number }) {
  if (painted === 0 && replaced === 0) return null;
  return (
    <span className="flex gap-1.5 text-[12.5px] font-semibold shrink-0">
      {painted > 0 && <span className="inline-flex items-center gap-1"><i className="w-2.5 h-2.5 rounded-sm bg-boyali" />{painted} boyalı</span>}
      {replaced > 0 && <span className="inline-flex items-center gap-1"><i className="w-2.5 h-2.5 rounded-sm bg-degisen" />{replaced} değişen</span>}
    </span>
  );
}

function Info({ label, value, numeric = false }: { label: string; value: string; numeric?: boolean }) {
  return (
    <div className="min-w-0">
      <dt className="etiket">{label}</dt>
      <dd className={`mt-1 truncate ${numeric ? "sayi text-[20px] leading-tight font-medium" : "text-[16px] font-semibold"}`}>{value}</dd>
    </div>
  );
}

function BackLink({ onBack, label }: { onBack: () => void; label: string }) {
  return <button type="button" onClick={onBack} className="basilir text-[15px] font-semibold text-plaka hover:underline">‹ {label}</button>;
}

function Loading() {
  return <p className="text-murekkep-3 py-10 text-center">Yükleniyor…</p>;
}

function Message({ title, text, action }: { title: string; text?: string; action?: ReactNode }) {
  return (
    <div className="min-h-dvh bg-zemin grid place-items-center px-4">
      <div className="kart p-6 max-w-sm text-center">
        <p className="font-cond font-bold text-[24px]">{title}</p>
        {text && <p className="mt-1 text-murekkep-2">{text}</p>}
        {action && <div className="mt-4">{action}</div>}
      </div>
    </div>
  );
}

