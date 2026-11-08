// Vitrin linkini kopyala / WhatsApp'ta paylas / Instagram'da paylas.
// Instagram'in disaridan link paylasma adresi yok: telefonda sistem paylasma menusu acilir (Instagram orada cikar);
// bilgisayarda link kopyalanir ve Instagram acilir, hikayeye "Baglanti" cikartmasi ya da profil linki olarak yapistirilir.
import { useState } from "react";
import { Button } from "../../shared/ui/Common";

export function ShowroomShareButtons({ link, galleryName }: { link: string; galleryName?: string }) {
  const [note, setNote] = useState<string | null>(null);
  const text = `${galleryName ? `${galleryName} · ` : ""}Satıştaki araçlarımız`;

  const copy = async (message = "Link kopyalandı") => {
    try {
      await navigator.clipboard.writeText(link);
      setNote(message);
    } catch {
      setNote("Kopyalanamadı; linki elle seç.");
    }
    setTimeout(() => setNote(null), 4000);
  };

  const whatsapp = () => window.open(`https://wa.me/?text=${encodeURIComponent(`${text}: ${link}`)}`, "_blank", "noopener");

  const instagram = async () => {
    const mobile = window.matchMedia("(pointer: coarse)").matches;
    if (mobile && navigator.share) {
      try {
        await navigator.share({ title: text, text, url: link });
        return;
      } catch (e) {
        if (e instanceof DOMException && e.name === "AbortError") return;
      }
    }
    await copy("Link kopyalandı. Instagram'da hikâyeye “Bağlantı” çıkartması ya da profil linki olarak yapıştır.");
    window.open("https://www.instagram.com/", "_blank", "noopener");
  };

  return (
    <div>
      {/* Telefonda uc esit kisa buton, genis ekranda ikonlu uzun etiket */}
      <div className="grid grid-cols-3 gap-2 sm:flex sm:flex-wrap">
        <Button variant="secondary" className="px-2 sm:px-4" onClick={() => void copy()}>
          <Icon d="M9 9h10v10H9zM5 15V5h10" />Kopyala
        </Button>
        <Button variant="secondary" className="px-2 sm:px-4" onClick={whatsapp}>
          <Icon d="M4 20l1.3-3.9A8 8 0 1 1 8 18.7z" />WhatsApp<span className="hidden sm:inline">'ta paylaş</span>
        </Button>
        <Button variant="secondary" className="px-2 sm:px-4" onClick={() => void instagram()}>
          <Icon d="M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4zM12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7zM17.3 6.7h.01" />Instagram<span className="hidden sm:inline">'da paylaş</span>
        </Button>
      </div>
      {note && <p role="status" className="mt-2 text-[13.5px] font-semibold text-basari">{note}</p>}
    </div>
  );
}

function Icon({ d }: { d: string }) {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden className="hidden sm:block mr-1.5 -ml-0.5">
      <path d={d} />
    </svg>
  );
}
