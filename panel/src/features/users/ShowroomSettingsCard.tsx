// Hesabim > Vitrin (dukkan sahibi): musteri sayfasinin linki (kopyala / paylas / ac), WhatsApp numarasi ve dukkan adresi
// (sahibinden aciklamasindaki galeri kartinda) ve galeri logosu (aciklamada ve hasar resminde).
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
  const [logoUrl, setLogoUrl] = useState(initial.logoUrl);
  const logoInput = useRef<HTMLInputElement>(null);

  const logo = async (action: () => Promise<{ logoUrl: string | null }>) => {
    setBusy(true);
    setError(null);
    try {
      setLogoUrl((await action()).logoUrl);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
      if (logoInput.current) logoInput.current.value = "";
    }
  };
  const pickLogo = (file: File | undefined) => {
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) return setError("Logo en fazla 5 MB olabilir.");
    void logo(async () => api.uploadLogo(await logoPng(file)));
  };
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const digits = phone.replace(/\D/g, "");

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      const saved = await api.updateShowroomSettings(digits || null, address.trim() || null);
      setSavedPhone(saved.phone ?? "");
      setSavedAddress(saved.address ?? "");
      setAddress(saved.address ?? "");
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
      <div>
        <p className="etiket mb-1.5">Galeri logosu</p>
        <div className="flex items-center gap-3">
          <div className="w-32 h-16 shrink-0 rounded-lg border border-cizgi bg-yuzey grid place-items-center overflow-hidden">
            {logoUrl ? <img src={apiUrl(logoUrl)} alt="Galeri logosu" className="max-w-full max-h-full object-contain p-1" />
              : <span className="text-[12.5px] text-murekkep-3">Logo yok</span>}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" type="button" disabled={busy} onClick={() => logoInput.current?.click()}>
              {logoUrl ? "Değiştir" : "Logo yükle"}
            </Button>
            {logoUrl && (
              <Button variant="secondary" type="button" disabled={busy}
                onClick={() => { if (window.confirm("Logo kaldırılsın mı?")) void logo(() => api.deleteLogo()); }}>
                Kaldır
              </Button>
            )}
          </div>
        </div>
        <p className="text-[13px] text-murekkep-2 mt-1.5">Sahibinden açıklamasında ve hasar resminde çıkar. Arka planı saydam PNG en iyi görünür.</p>
        <input ref={logoInput} type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={(e) => pickLogo(e.target.files?.[0])} />
      </div>
      <Field label="Dükkân adresi" hint="Sahibinden açıklamasında galeri kartında görünür. Boşsa yazılmaz.">
        <textarea className={`${inputClass} h-auto min-h-20 py-2`} rows={2} maxLength={300} value={address}
          onChange={(e) => setAddress(e.target.value)} placeholder="Saracalar Mah. Özal Blv. No:228 Akyurt / Ankara" />
      </Field>
      <div className="flex justify-end">
        <Button onClick={() => void save()} disabled={busy || (digits === savedPhone && address.trim() === savedAddress)}>Kaydet</Button>
      </div>
    </div>
  );
}
