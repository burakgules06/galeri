// Arac detayinda "Vitrin" karti (sadece dukkan sahibi): fotograflar, vitrinde gosterme, ilan fiyati ve aciklamasi.
// Fotograflar yuklenince hemen kaydedilir; diger alanlar "Kaydet" ile. Ilk fotograf kapak olur.
import { useRef, useState } from "react";
import { api } from "../../api/galeri";
import { apiUrl } from "../../api/client";
import { centsToInput, tlToCents } from "../../shared/format";
import { shrinkImage } from "../../shared/image/shrinkImage";
import type { ShowroomListing, ShowroomPhoto } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";

const MAX_PHOTOS = 20;

export function ShowroomCard({ vehicleId }: { vehicleId: string }) {
  const listing = useLoad(() => api.listing(vehicleId), [vehicleId]);
  const settings = useLoad(() => api.showroomSettings(), []);
  return (
    <section className="kart p-4 md:p-5">
      <div className="flex items-center justify-between gap-3 pb-3 mb-4 border-b border-cizgi">
        <h2 className="font-cond font-bold text-[21px] tracking-tight">Vitrin</h2>
        {listing.data?.showInShowroom && listing.data.inStock && settings.data && (
          <a className="text-[14px] font-semibold text-plaka hover:underline" target="_blank" rel="noreferrer"
            href={`/vitrin/${settings.data.slug}/${vehicleId}`}>Müşteri gözüyle gör ›</a>
        )}
      </div>
      {listing.error && <ErrorBox message={listing.error} />}
      {listing.data && <ShowroomEditor key={vehicleId} vehicleId={vehicleId} initial={listing.data} />}
    </section>
  );
}

function ShowroomEditor({ vehicleId, initial }: { vehicleId: string; initial: ShowroomListing }) {
  const [listing, setListing] = useState(initial);
  const [show, setShow] = useState(initial.showInShowroom);
  const [price, setPrice] = useState(initial.listingPriceCents ? centsToInput(initial.listingPriceCents) : "");
  const [description, setDescription] = useState(initial.listingDescription ?? "");
  const [uploading, setUploading] = useState<{ done: number; total: number } | null>(null);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const photos = listing.photos;
  const dirty = show !== listing.showInShowroom
    || (price ? tlToCents(price) : null) !== listing.listingPriceCents
    || (description.trim() || null) !== listing.listingDescription;

  const upload = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const list = Array.from(files).slice(0, MAX_PHOTOS - photos.length);
    setError(files.length > list.length ? `En fazla ${MAX_PHOTOS} fotoğraf; ilk ${list.length} tanesi ekleniyor.` : null);
    setUploading({ done: 0, total: list.length });
    try {
      for (let i = 0; i < list.length; i++) {
        setListing(await api.addPhoto(vehicleId, await shrinkImage(list[i])));
        setUploading({ done: i + 1, total: list.length });
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setUploading(null);
      if (fileInput.current) fileInput.current.value = "";
    }
  };

  const run = async (action: () => Promise<ShowroomListing>) => {
    setBusy(true);
    setError(null);
    try {
      setListing(await action());
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const move = (index: number, to: number) => {
    const ids = photos.map((p) => p.id);
    const [id] = ids.splice(index, 1);
    ids.splice(to, 0, id);
    return run(() => api.reorderPhotos(vehicleId, ids));
  };

  const save = async () => {
    const cents = price ? tlToCents(price) : null;
    if (price && (cents == null || Number.isNaN(cents) || cents <= 0)) return setError("Fiyatı 850.000 gibi yaz ya da boş bırak.");
    setSaved(false);
    await run(() => api.updateListing(vehicleId, { showInShowroom: show, listingPriceCents: cents, listingDescription: description.trim() || null }));
    setSaved(true);
  };

  return (
    <div className="space-y-4">
      <ErrorBox message={error} />

      <label className={`flex items-start gap-3 cursor-pointer ${listing.inStock ? "" : "opacity-60"}`}>
        <input type="checkbox" className="w-5 h-5 mt-0.5 accent-plaka" checked={show} disabled={!listing.inStock}
          onChange={(e) => { setShow(e.target.checked); setSaved(false); }} />
        <span>
          <span className="block font-semibold text-[16px]">Vitrinde göster</span>
          <span className="block text-[13.5px] text-murekkep-2">
            {listing.inStock ? "Müşteri sayfasında fotoğraflarıyla listelenir. Satılınca kendiliğinden kalkar." : "Sadece stoktaki araçlar vitrinde görünür."}
          </span>
        </span>
      </label>

      <div>
        <div className="flex items-baseline justify-between mb-2">
          <p className="etiket">Fotoğraflar <span className="sayi text-[13px] text-murekkep-3 normal-case">{photos.length}/{MAX_PHOTOS}</span></p>
          {photos.length > 1 && <p className="text-[12.5px] text-murekkep-3">İlk fotoğraf kapak olur</p>}
        </div>
        <div className="grid grid-cols-3 sm:grid-cols-4 gap-2">
          {photos.map((p, i) => (
            <PhotoTile key={p.id} photo={p} cover={i === 0} disabled={busy || !!uploading}
              onCover={i > 0 ? () => move(i, 0) : undefined}
              onLeft={i > 0 ? () => move(i, i - 1) : undefined}
              onRight={i < photos.length - 1 ? () => move(i, i + 1) : undefined}
              onDelete={() => { if (window.confirm("Fotoğraf silinsin mi?")) void run(() => api.deletePhoto(vehicleId, p.id)); }} />
          ))}
          {photos.length < MAX_PHOTOS && (
            <button type="button" onClick={() => fileInput.current?.click()} disabled={!!uploading}
              className="basilir aspect-[4/3] rounded-lg border-2 border-dashed border-cizgi text-murekkep-2 hover:border-plaka hover:text-plaka flex flex-col items-center justify-center gap-1 text-[13.5px] font-semibold">
              {uploading ? <>Yükleniyor<span className="sayi">{uploading.done}/{uploading.total}</span></> : <><span className="text-[24px] leading-none">+</span>Fotoğraf ekle</>}
            </button>
          )}
        </div>
        <input ref={fileInput} type="file" accept="image/*" multiple hidden onChange={(e) => void upload(e.target.files)} />
      </div>

      <Field label="İlan fiyatı" hint="Boş bırakırsan vitrinde “Fiyat için arayın” yazar.">
        <NumberInput className={`${inputClass} font-cond text-[18px]`} value={price} onValueChange={(v) => { setPrice(v); setSaved(false); }} placeholder="850.000" />
      </Field>
      <Field label="İlan açıklaması" hint="Müşteri görür. Aracın iç notları burada görünmez.">
        <textarea className={`${inputClass} h-auto min-h-24 py-2`} rows={4} maxLength={4000} value={description}
          onChange={(e) => { setDescription(e.target.value); setSaved(false); }}
          placeholder="Servis bakımlı, ilk sahibinden, yeni muayeneli…" />
      </Field>

      <div className="flex items-center justify-end gap-3">
        {saved && !dirty && <span role="status" className="text-[14px] font-semibold text-basari">Kaydedildi</span>}
        <Button onClick={() => void save()} disabled={busy || !dirty}>{busy ? "Kaydediliyor…" : "Kaydet"}</Button>
      </div>
    </div>
  );
}

function PhotoTile({ photo, cover, disabled, onCover, onLeft, onRight, onDelete }: {
  photo: ShowroomPhoto; cover: boolean; disabled: boolean;
  onCover?: () => void; onLeft?: () => void; onRight?: () => void; onDelete: () => void;
}) {
  const small = "basilir grid place-items-center w-8 h-8 rounded-md bg-gece/75 text-white text-[14px] disabled:opacity-40";
  return (
    <figure className="relative aspect-[4/3] rounded-lg overflow-hidden bg-zemin border border-cizgi">
      <img src={apiUrl(photo.thumbUrl)} alt="" loading="lazy" className="w-full h-full object-cover" />
      {cover && <span className="absolute top-1.5 left-1.5 rounded bg-plaka text-white text-[11px] font-bold px-1.5 py-0.5">KAPAK</span>}
      <div className="absolute inset-x-1.5 bottom-1.5 flex justify-between gap-1">
        <span className="flex gap-1">
          {onLeft && <button type="button" className={small} disabled={disabled} onClick={onLeft} aria-label="Sola al">‹</button>}
          {onRight && <button type="button" className={small} disabled={disabled} onClick={onRight} aria-label="Sağa al">›</button>}
        </span>
        <span className="flex gap-1">
          {onCover && <button type="button" className={`${small} w-auto px-2 text-[12px] font-semibold`} disabled={disabled} onClick={onCover}>Kapak</button>}
          <button type="button" className={`${small} hover:bg-tehlike`} disabled={disabled} onClick={onDelete} aria-label="Sil">✕</button>
        </span>
      </div>
    </figure>
  );
}
