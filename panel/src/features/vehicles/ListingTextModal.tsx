// "Sahibinden açıklaması": aracin bilgilerinden kurumsal ilan duzeninde aciklama. Kopyala ile sahibinden'in aciklama alanina
// yapistirilir (bicim destekleniyorsa tablolar / renkler, desteklenmiyorsa duzenli duz metin gelir). Hasar semasi ayrica resim
// olarak indirilip ilana fotograf olarak eklenir.
import { useState } from "react";
import { copyRich, copyText, saveFile } from "../../shared/clipboard";
import type { VehicleDetail } from "../../shared/types";
import { Button, ErrorBox, Modal } from "../../shared/ui/Common";
import { damageImage } from "./damageImage";
import { listingHtml, listingPlainText } from "./listingText";

export function ListingTextModal({ vehicle, galleryName, phone, address, description, onClose }: {
  vehicle: VehicleDetail;
  galleryName: string;
  phone: string | null;
  address: string | null;
  description: string;
  onClose: () => void;
}) {
  const [showPlate, setShowPlate] = useState(true);
  const [showPhone, setShowPhone] = useState(true);
  const [done, setDone] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const input = { vehicle, galleryName, phone, address, description, showPlate, showPhone };
  const html = listingHtml(input);
  const text = listingPlainText(input);

  const act = async (label: string, action: () => Promise<boolean | void>) => {
    setBusy(true);
    setError(null);
    setDone(null);
    try {
      const ok = await action();
      if (ok === false) setError("Kopyalanamadı. Tarayıcı izin vermedi; tekrar dene.");
      else setDone(label);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Bir hata oluştu");
    } finally {
      setBusy(false);
    }
  };

  const plateFile = vehicle.plate.replace(/\s+/g, "") || "arac";
  return (
    <Modal title="Sahibinden açıklaması" open onClose={onClose} wide
      footer={<>
        <Button variant="secondary" type="button" disabled={busy}
          onClick={() => act("Hasar şeması hazır", async () => saveFile(await damageImage(vehicle.parts, `${vehicle.make} ${vehicle.model} · ${vehicle.modelYear}`), `${plateFile}-hasar.png`))}>
          Hasar resmi
        </Button>
        <Button variant="secondary" type="button" disabled={busy} onClick={() => act("Düz metin kopyalandı", () => copyText(text))}>
          Düz metin
        </Button>
        <Button type="button" disabled={busy} onClick={() => act("Kopyalandı. Sahibinden'de açıklama alanına yapıştır.", () => copyRich(html, text))}>
          Kopyala
        </Button>
      </>}>
      <div className="space-y-3">
        <p className="text-[14px] text-murekkep-2">
          Kopyala'ya bas, sahibinden'de ilanın açıklama alanına yapıştır. Biçim desteklenmezse aynı bilgiler düzenli metin olarak gelir.
          Hasar şemasını resim olarak indirip ilana fotoğraf olarak ekleyebilirsin.
        </p>
        <div className="flex flex-wrap gap-x-5 gap-y-2">
          <label className="flex items-center gap-2 text-[14px] cursor-pointer">
            <input type="checkbox" className="w-5 h-5 accent-plaka" checked={showPlate} onChange={(e) => setShowPlate(e.target.checked)} />
            Plakayı göster
          </label>
          <label className={`flex items-center gap-2 text-[14px] ${phone ? "cursor-pointer" : "opacity-60"}`}>
            <input type="checkbox" className="w-5 h-5 accent-plaka" checked={showPhone && !!phone} disabled={!phone}
              onChange={(e) => setShowPhone(e.target.checked)} />
            Telefonu göster{!phone && " (vitrin ayarlarında telefon yok)"}
          </label>
          {!address && <span className="text-[13.5px] text-murekkep-2">Dükkân adresi yok: Hesabım › Müşteri vitrini'nden eklenir.</span>}
        </div>
        <ErrorBox message={error} />
        {done && <p role="status" className="text-[14px] font-semibold text-basari">{done}</p>}
        {/* Onizleme: yapistirilinca bicim destekleniyorsa boyle gorunur */}
        <div className="rounded-lg border border-cizgi bg-yuzey p-3 max-h-[55vh] overflow-auto" dangerouslySetInnerHTML={{ __html: html }} />
      </div>
    </Modal>
  );
}
