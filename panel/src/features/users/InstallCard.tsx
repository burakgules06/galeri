// Hesabim'da "Telefona kur": Android'de tek dokunusla kurulum, iPhone'da Paylas > Ana Ekrana Ekle tarifi.
// Uygulama zaten kurulu acildiysa ya da tarayici kurulumu desteklemiyorsa gorunmez.
import { useEffect, useState } from "react";
import { canPromptInstall, isIos, isStandalone, onInstallChange, promptInstall } from "../../shared/pwa/install";
import { Button } from "../../shared/ui/Common";

export function InstallCard() {
  const [canPrompt, setCanPrompt] = useState(canPromptInstall);
  useEffect(() => onInstallChange(() => setCanPrompt(canPromptInstall())), []);

  if (isStandalone()) return null;
  const ios = isIos();
  if (!ios && !canPrompt) return null;

  return (
    <div className="kart p-5 mb-4 flex gap-4 items-start">
      <img src="/icon-192.png" alt="" className="w-12 h-12 rounded-[12px] shrink-0" />
      <div className="grow min-w-0">
        <p className="etiket">Telefona kur</p>
        <p className="font-semibold text-[16px] mt-0.5">Paneli uygulama gibi ana ekrandan açın</p>
        {ios ? (
          <ol className="text-[14px] text-murekkep-2 mt-2 space-y-1 list-decimal pl-5">
            <li>Safari'de alttaki <b className="text-murekkep">Paylaş</b> düğmesine dokunun.</li>
            <li><b className="text-murekkep">Ana Ekrana Ekle</b>'yi seçin, sonra <b className="text-murekkep">Ekle</b>.</li>
            <li>Ana ekrandaki <b className="text-murekkep">Galeri</b> simgesinden açın. Ödeme bildirimleri iPhone'da sadece böyle çalışır.</li>
          </ol>
        ) : (
          <>

            <Button className="mt-3" onClick={() => { void promptInstall(); }}>Ana ekrana ekle</Button>
          </>
        )}
      </div>
    </div>
  );
}
