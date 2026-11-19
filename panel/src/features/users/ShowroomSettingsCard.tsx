// Hesabim > Vitrin (dukkan sahibi): musteri sayfasinin linki (kopyala / paylas / ac), WhatsApp numarasi ve dukkan adresi
// (sahibinden aciklamasindaki galeri kartinda), galeri logosu (aciklamada ve hasar resminde) ve alt banner (aciklamanin en
// altinda, logo ile ayni genislikte).
import { useRef, useState } from "react";
import { apiUrl } from "../../api/client";
import { logoPng } from "../../shared/image/logoPng";
import { api } from "../../api/galeri";
import { formatPhone } from "../../shared/format";
import type { ShowroomSettings } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";
import { ShowroomShareButtons } from "../showroom/ShowroomShareButtons";

export function ShowroomSettingsCard() {
  const settings = useLoad(() => api.showroomSettings(), []);
  return (
    <section className="kart p-5 mb-4">
      <p className="etiket mb-1">Müşteri vitrini</p>
      <p className="text-[14px] text-murekkep-2 mb-3">
        Araç sayfasında “Vitrinde göster” işaretlediğin stok araçları bu linkte fotoğraflarıyla listelenir. Alış fiyatı, maliyet ve iç notlar görünmez.
      </p>
      {settings.error && <ErrorBox message={settings.error} />}
      {settings.data && <SettingsForm key={settings.data.slug} initial={settings.data} />}
    </section>
  );
}

function SettingsForm({ initial }: { initial: ShowroomSettings }) {
  const link = `${window.location.origin}/vitrin/${initial.slug}`;
  const [phone, setPhone] = useState(initial.phone ? formatPhone(initial.phone) : "");
  const [savedPhone, setSavedPhone] = useState(initial.phone ?? "");
  const [address, setAddress] = useState(initial.address ?? "");
  const [savedAddress, setSavedAddress] = useState(initial.address ?? "");
  const [license, setLicense] = useState(initial.dealerLicenseNo ?? "");
  const [savedLicense, setSavedLicense] = useState(initial.dealerLicenseNo ?? "");
  const [logoUrl, setLogoUrl] = useState(initial.logoUrl);
  const [footerBannerUrl, setFooterBannerUrl] = useState(initial.footerBannerUrl);

  // Logo ve alt banner ayni sekilde yuklenir: PNG'ye cevrilir, cevaptaki adresler guncellenir
  const image = async (action: () => Promise<ShowroomSettings>) => {
    setBusy(true);
    setError(null);
    try {
      const saved = await action();
      setLogoUrl(saved.logoUrl);
      setFooterBannerUrl(saved.footerBannerUrl);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  const pick = (file: File, upload: (png: Blob) => Promise<ShowroomSettings>) => {
    if (file.size > 5 * 1024 * 1024) return setError("Görsel en fazla 5 MB olabilir.");
    void image(async () => upload(await logoPng(file)));
  };
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const digits = phone.replace(/\D/g, "");

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      const saved = await api.updateShowroomSettings(digits || null, address.trim() || null, license.trim() || null);
      setSavedPhone(saved.phone ?? "");
      setSavedAddress(saved.address ?? "");
      setAddress(saved.address ?? "");
      setSavedLicense(saved.dealerLicenseNo ?? "");
      setLicense(saved.dealerLicenseNo ?? "");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <ErrorBox message={error} />
      <div className="rounded-lg bg-zemin border border-cizgi px-3 py-2.5 text-[15px] font-semibold break-all">{link}</div>
      <ShowroomShareButtons link={link} />
      <Field label="WhatsApp numarası" hint="Müşteri bu numaraya yazar ya da arar. Boşsa buton çıkmaz.">
        <NumberInput kind="phone" value={phone} onValueChange={setPhone} placeholder="0530 111 22 33" />
      </Field>
      <ImageField label="Galeri logosu" url={logoUrl} busy={busy} boxClass="w-32 h-16"
        hint="Sahibinden açıklamasında ve hasar resminde çıkar. Arka planı saydam PNG en iyi görünür."
        onPick={(f) => pick(f, api.uploadLogo)}
        onRemove={() => { if (window.confirm("Logo kaldırılsın mı?")) void image(() => api.deleteLogo()); }} />
      <ImageField label="Alt banner" url={footerBannerUrl} busy={busy} boxClass="w-48 h-16"
        hint="Sahibinden açıklamasının en altında, üstteki logo ile aynı genişlikte çıkar. Yatay (geniş) görsel en iyi görünür."
        onPick={(f) => pick(f, api.uploadFooterBanner)}
        onRemove={() => { if (window.confirm("Alt banner kaldırılsın mı?")) void image(() => api.deleteFooterBanner()); }} />
      <Field label="Dükkân adresi" hint="Sahibinden açıklamasının düz metin halinde (Satıcı bölümü) yazılır. Boşsa yazılmaz.">
        <textarea className={`${inputClass} h-auto min-h-20 py-2`} rows={2} maxLength={300} value={address}
          onChange={(e) => setAddress(e.target.value)} placeholder="Saracalar Mah. Özal Blv. No:228 Akyurt / Ankara" />
      </Field>
      <Field label="Yetki belge no" hint="İkinci el araç ticareti yetki belgesi. Sahibinden açıklamasındaki bilgi kartlarında çıkar. Boşsa çıkmaz.">
        <input className={inputClass} maxLength={40} value={license} onChange={(e) => setLicense(e.target.value)} placeholder="06-123456" />
      </Field>
      <div className="flex justify-end">
        <Button onClick={() => void save()}
          disabled={busy || (digits === savedPhone && address.trim() === savedAddress && license.trim() === savedLicense)}>Kaydet</Button>
      </div>
    </div>
  );
}

function ImageField({ label, url, hint, busy, boxClass, onPick, onRemove }: {
  label: string; url: string | null; hint: string; busy: boolean; boxClass: string;
  onPick: (file: File) => void; onRemove: () => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  return (
    <div>
      <p className="etiket mb-1.5">{label}</p>
      <div className="flex items-center gap-3">
        <div className={`${boxClass} shrink-0 rounded-lg border border-cizgi bg-yuzey grid place-items-center overflow-hidden`}>
          {url ? <img src={apiUrl(url)} alt={label} className="max-w-full max-h-full object-contain p-1" />
            : <span className="text-[12.5px] text-murekkep-3">Yok</span>}
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" type="button" disabled={busy} onClick={() => input.current?.click()}>
            {url ? "Değiştir" : "Yükle"}
          </Button>
          {url && <Button variant="secondary" type="button" disabled={busy} onClick={onRemove}>Kaldır</Button>}
        </div>
      </div>
      <p className="text-[13px] text-murekkep-2 mt-1.5">{hint}</p>
      <input ref={input} type="file" accept="image/png,image/jpeg,image/webp" hidden
        onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ""; if (f) onPick(f); }} />
    </div>
  );
}
