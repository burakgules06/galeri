// Hesabim > Vitrin (dukkan sahibi): musteri sayfasinin linki (kopyala / paylas / ac) ve WhatsApp numarasi.
import { useState } from "react";
import { api } from "../../api/galeri";
import { formatPhone } from "../../shared/format";
import type { ShowroomSettings } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";

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
  const [copied, setCopied] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const digits = phone.replace(/\D/g, "");

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setError("Kopyalanamadı; linki elle seç.");
    }
  };
  const share = () => navigator.share?.({ title: "Araçlarımız", url: link }).catch(() => {});
  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      setSavedPhone((await api.updateShowroomSettings(digits || null)).phone ?? "");
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
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" onClick={() => void copy()}>{copied ? "Kopyalandı" : "Linki kopyala"}</Button>
        {"share" in navigator && <Button variant="secondary" onClick={share}>Paylaş</Button>}
        <a href={link} target="_blank" rel="noreferrer" className="basilir inline-flex items-center h-10 px-3 text-[15px] font-semibold text-plaka hover:underline">Vitrini aç ›</a>
      </div>
      <div className="flex items-end gap-2">
        <Field label="WhatsApp numarası" hint="Müşteri bu numaraya yazar ya da arar. Boşsa buton çıkmaz." className="grow">
          <NumberInput kind="phone" value={phone} onValueChange={setPhone} placeholder="0530 111 22 33" />
        </Field>
        <Button className="mb-6" onClick={() => void save()} disabled={busy || digits === savedPhone}>Kaydet</Button>
      </div>
    </div>
  );
}
