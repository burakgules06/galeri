// Araclar sayfasinin ustunde: musteri vitrini linki ve paylasma butonlari (dukkan sahibi).
import { api } from "../../api/galeri";
import { useAuth } from "../../auth/useAuth";
import { useLoad } from "../../shared/useLoad";
import { ShowroomShareButtons } from "./ShowroomShareButtons";

export function ShowroomShareBar() {
  const { auth } = useAuth();
  const settings = useLoad(() => api.showroomSettings(), []);
  if (!settings.data) return null;
  const link = `${window.location.origin}/vitrin/${settings.data.slug}`;
  return (
    <section className="kart p-3 md:p-4 mb-4 flex flex-col lg:flex-row lg:items-center gap-3 lg:justify-between">
      <div className="min-w-0">
        <p className="etiket">Müşteri vitrini</p>
        <a href={link} target="_blank" rel="noreferrer" className="block truncate text-[15px] font-semibold text-plaka hover:underline">
          {link.replace(/^https?:\/\//, "")}
        </a>
      </div>
      <ShowroomShareButtons link={link} galleryName={auth?.tenantName} />
    </section>
  );
}
