// Uygulama iskeleti: telefonda ust cubuk + alt sekmeler, genis ekranda sol menu.
import type { ReactNode } from "react";
import { useAuth } from "../auth/useAuth";
import { AracDetaySayfasi } from "../features/araclar/AracDetaySayfasi";
import { AracListesi } from "../features/araclar/AracListesi";
import { AylikGorunum } from "../features/aylik/AylikGorunum";
import { KisiListesi } from "../features/kisiler/KisiListesi";
import { ParaSayfasi } from "../features/para/ParaSayfasi";
import { useRota, type Rota } from "../shared/useRota";

type Bolum = "araclar" | "para" | "aylik" | "kisiler";

const IKON: Record<Bolum, ReactNode> = {
  araclar: <path d="M5 16l1.6-5.2A2 2 0 0 1 8.5 9.4h7a2 2 0 0 1 1.9 1.4L19 16M4 16h16v3a1 1 0 0 1-1 1h-1.5a1 1 0 0 1-1-1v-1h-9v1a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM7.5 13.5h.01M16.5 13.5h.01" />,
  aylik: <path d="M4 19V9M10 19V5M16 19v-7M3 19h18" />,
  para: <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6h15A1.5 1.5 0 0 1 21 7.5v9a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 16.5zM3 10h18M7 14.5h3" />,
  kisiler: <path d="M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20c.8-3.5 3.4-5.5 6.5-5.5s5.7 2 6.5 5.5M16 4.5a3.5 3.5 0 0 1 0 7M18 14.6c2 .7 3.2 2.6 3.6 5.4" />,
};

const ETIKET: Record<Bolum, string> = { araclar: "Araçlar", para: "Kasa", aylik: "Alım ve satım", kisiler: "Kişiler" };

function bolumu(rota: Rota): Bolum {
  return rota.sayfa === "arac" ? "araclar" : rota.sayfa;
}

export function Dashboard() {
  const { auth, isAdmin, logout } = useAuth();
  const rota = useRota();
  const bolumler: Bolum[] = isAdmin ? ["araclar", "para", "aylik", "kisiler"] : ["araclar", "aylik"];
  const aktif = bolumu(rota);

  const icerik =
    rota.sayfa === "arac" ? <AracDetaySayfasi id={rota.id} />
    : rota.sayfa === "aylik" ? <AylikGorunum />
    : rota.sayfa === "kisiler" && isAdmin ? <KisiListesi />
    : rota.sayfa === "para" && isAdmin ? <ParaSayfasi />
    : <AracListesi />;

  return (
    <div className="min-h-screen md:flex">
      {/* Genis ekran: sol menu */}
      <aside className="hidden md:flex md:flex-col w-60 shrink-0 bg-yuzey border-r border-cizgi h-screen sticky top-0">
        <div className="px-5 pt-6 pb-5">
          <p className="font-cond font-bold text-[24px] leading-tight tracking-tight">{auth?.tenantName || "Galeri"}</p>
        </div>
        <nav className="px-3 space-y-0.5 grow">
          {bolumler.map((b) => (
            <a key={b} href={`#/${b}`} aria-current={aktif === b ? "page" : undefined}
              className={`flex items-center gap-3 h-10 px-3 rounded-lg font-semibold text-[15px] ${
                aktif === b ? "bg-plaka-acik text-plaka-koyu" : "text-murekkep-2 hover:bg-yuzey-2 hover:text-murekkep"}`}>
              <Ikon>{IKON[b]}</Ikon>{ETIKET[b]}
            </a>
          ))}
        </nav>
        <div className="px-5 py-4 border-t border-cizgi">
          <p className="font-semibold text-[15px] truncate">{auth?.fullName}</p>
          <button onClick={logout} className="text-[14px] text-murekkep-2 hover:text-murekkep">Çıkış yap</button>
        </div>
      </aside>

      <div className="grow min-w-0">
        {/* Telefon: ust cubuk */}
        <header className="md:hidden sticky top-0 z-20 bg-yuzey border-b border-cizgi h-14 px-4 flex items-center justify-between">
          <p className="font-cond font-bold text-[21px] tracking-tight truncate">{auth?.tenantName || "Galeri"}</p>
          <button onClick={logout} aria-label="Çıkış yap" className="basilir w-10 h-10 rounded-lg grid place-items-center text-murekkep-2 hover:bg-yuzey-2">
            <Ikon><path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l5-5-5-5M15 12H4" /></Ikon>
          </button>
        </header>

        <main className="px-4 md:px-8 py-5 md:py-8 pb-28 md:pb-10 max-w-6xl">{icerik}</main>
      </div>

      {/* Telefon: alt sekmeler */}
      <nav className="md:hidden fixed bottom-0 inset-x-0 z-20 bg-yuzey border-t border-cizgi flex pb-[env(safe-area-inset-bottom)]">
        {bolumler.map((b) => (
          <a key={b} href={`#/${b}`} aria-current={aktif === b ? "page" : undefined}
            className={`grow flex flex-col items-center gap-0.5 pt-2.5 pb-2 text-[12px] font-semibold ${aktif === b ? "text-plaka" : "text-murekkep-3"}`}>
            <Ikon>{IKON[b]}</Ikon>{ETIKET[b]}
          </a>
        ))}
      </nav>
    </div>
  );
}

function Ikon({ children }: { children: ReactNode }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {children}
    </svg>
  );
}
