// Uygulama iskeleti. Genis ekran: koyu sol menu, varsayilan olarak dar (sadece ikonlar); dugmeyle acilir,
// tercih tarayicida hatirlanir. Telefon: ust cubuk + alt sekmeler.
import { useState, type ReactNode } from "react";
import { useAuth } from "../auth/useAuth";
import { ContactDetailPage } from "../features/contacts/ContactDetailPage";
import { ContactList } from "../features/contacts/ContactList";
import { PaymentsPage } from "../features/payments/PaymentsPage";
import { HomePage } from "../features/home/HomePage";
import { LedgerPage } from "../features/ledger/LedgerPage";
import { ExpensesPage } from "../features/expenses/ExpensesPage";
import { ShopPage } from "../features/expenses/ShopPage";
import { MonthlyView } from "../features/monthly/MonthlyView";
import { VehicleDetailPage } from "../features/vehicles/VehicleDetailPage";
import { VehicleList } from "../features/vehicles/VehicleList";
import { AccountPage } from "../features/users/AccountPage";
import { UsersPage } from "../features/users/UsersPage";
import { disablePush } from "../shared/push/push";
import { useRoute, type Route } from "../shared/useRoute";
import { NotificationButton } from "./NotificationButton";

type Section = "home" | "vehicles" | "payments" | "ledger" | "expenses" | "shop" | "monthly" | "contacts" | "users";

const ICON: Record<Section, ReactNode> = {
  home: <path d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z" />,
  vehicles: <path d="M5 16l1.6-5.2A2 2 0 0 1 8.5 9.4h7a2 2 0 0 1 1.9 1.4L19 16M4 16h16v3a1 1 0 0 1-1 1h-1.5a1 1 0 0 1-1-1v-1h-9v1a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM7.5 13.5h.01M16.5 13.5h.01" />,
  payments: <path d="M7 3.5h10a1 1 0 0 1 1 1V21l-3-2-3 2-3-2-3 2V4.5a1 1 0 0 1 1-1zM9 8.5h6M9 12h6M9 15.5h3" />,
  monthly: <path d="M4 19V9M10 19V5M16 19v-7M3 19h18" />,
  expenses: <path d="M6 3.5h12v17l-2-1.3-2 1.3-2-1.3-2 1.3-2-1.3-2 1.3zM9 8h6M9 11.5h6M9 15h3.5" />,
  shop: <path d="M4 9.5 5.5 4h13L20 9.5M4 9.5h16M4 9.5a2.7 2.7 0 0 0 5.3 0 2.7 2.7 0 0 0 5.4 0 2.7 2.7 0 0 0 5.3 0M5.5 12v8h13v-8M10 20v-4.5h4V20" />,
  ledger: <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6h15A1.5 1.5 0 0 1 21 7.5v9a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 16.5zM3 10h18M7 14.5h3" />,
  contacts: <path d="M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20c.8-3.5 3.4-5.5 6.5-5.5s5.7 2 6.5 5.5M16 4.5a3.5 3.5 0 0 1 0 7M18 14.6c2 .7 3.2 2.6 3.6 5.4" />,
  users: <path d="M12 3.5l7 2.6v5.2c0 4.4-3 7.9-7 9.2-4-1.3-7-4.8-7-9.2V6.1zM12 11.5a2.3 2.3 0 1 0 0-4.6 2.3 2.3 0 0 0 0 4.6zM8.3 16c.6-1.8 2-2.8 3.7-2.8s3.1 1 3.7 2.8" />,
};
const ACCOUNT_ICON = <path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4.5 20.5c.9-3.6 3.9-5.8 7.5-5.8s6.6 2.2 7.5 5.8" />;

const LABEL: Record<Section, string> = {
  home: "Ana sayfa", vehicles: "Araçlar", payments: "Ödemeler", ledger: "Kasa", expenses: "Masraflar", shop: "Dükkân", monthly: "Alım ve satım",
  contacts: "Kişiler", users: "Kullanıcılar",
};
const SHORT_LABEL: Record<Section, string> = {
  home: "Ana sayfa", vehicles: "Araçlar", payments: "Ödemeler", ledger: "Kasa", expenses: "Masraf", shop: "Dükkân", monthly: "Alım-satım",
  contacts: "Kişiler", users: "Kullanıcılar",
};

const MENU_KEY = "galeri_menu_open";

function sectionOf(route: Route): Section | null {
  if (route.page === "vehicle") return "vehicles";
  if (route.page === "contact") return "contacts";
  if (route.page === "account") return null;
  return route.page;
}

function readMenuOpen(): boolean {
  try { return localStorage.getItem(MENU_KEY) === "1"; } catch { return false; }
}

export function Dashboard() {
  const { auth, isAdmin, logout: endSession } = useAuth();
  // Cikista bu cihazin bildirim aboneligi de kapanir (ayni cihazda calisan oturum acarsa tutar gormesin)
  const logout = () => { disablePush().catch(() => {}).finally(endSession); };
  const route = useRoute();
  const [open, setOpen] = useState(readMenuOpen);
  const sections: Section[] = isAdmin
    ? ["home", "vehicles", "payments", "ledger", "expenses", "shop", "monthly", "contacts", "users"] : ["home", "vehicles", "monthly"];
  // Telefon alt sekmesi dolu: Kullanicilar'a Hesabim'dan, Masraflar ve Dukkan'a Kasa sayfasindan gidilir
  const tabs = sections.filter((s) => s !== "users" && s !== "expenses" && s !== "shop");
  const active = sectionOf(route);
  const initials = (auth?.tenantName || "Galeri").split(" ").map((w) => w[0]).slice(0, 2).join("").toLocaleUpperCase("tr-TR");

  const toggle = () => setOpen((o) => {
    try { localStorage.setItem(MENU_KEY, o ? "0" : "1"); } catch { /* tarayici depolamasi kapali olabilir */ }
    return !o;
  });

  const content =
    route.page === "vehicle" ? <VehicleDetailPage key={route.id} id={route.id} />
    : route.page === "vehicles" ? <VehicleList />
    : route.page === "monthly" ? <MonthlyView />
    : route.page === "payments" && isAdmin ? <PaymentsPage />
    : route.page === "contact" && isAdmin ? <ContactDetailPage id={route.id} />
    : route.page === "contacts" && isAdmin ? <ContactList />
    : route.page === "ledger" && isAdmin ? <LedgerPage />
    : route.page === "expenses" && isAdmin ? <ExpensesPage />
    : route.page === "shop" && isAdmin ? <ShopPage />
    : route.page === "users" && isAdmin ? <UsersPage />
    : route.page === "account" ? <AccountPage />
    : <HomePage />;

  return (
    <div className="min-h-screen md:flex">
      {/* Genis ekran: koyu sol menu */}
      <aside className={`hidden md:flex md:flex-col shrink-0 bg-gece text-gece-yazi h-screen sticky top-0 transition-[width] duration-200 ${open ? "w-60" : "w-[76px]"}`}>
        <div className={`flex items-center gap-3 h-[72px] border-b border-gece-cizgi ${open ? "px-5" : "justify-center"}`}>
          <span className="w-10 h-10 rounded-[10px] bg-seri-1 text-white grid place-items-center font-cond font-bold text-[17px] shrink-0">{initials}</span>
          {open && <p className="font-cond font-bold text-[20px] leading-tight tracking-tight text-white truncate">{auth?.tenantName || "Galeri"}</p>}
        </div>

        <nav className="px-3 pt-4 space-y-1 grow">
          {sections.map((s) => (
            <a key={s} href={`#/${s === "home" ? "" : s}`} aria-current={active === s ? "page" : undefined} title={open ? undefined : LABEL[s]}
              className={`relative flex items-center gap-3 h-11 rounded-[10px] font-semibold text-[15px] transition-colors ${open ? "px-3" : "justify-center"} ${
                active === s ? "bg-gece-2 text-white" : "hover:bg-gece-2/60 hover:text-white"}`}>
              {active === s && <span className="absolute left-0 top-2.5 bottom-2.5 w-[3px] rounded-r bg-seri-1" />}
              <Icon>{ICON[s]}</Icon>{open && LABEL[s]}
            </a>
          ))}
        </nav>

        <div className={`border-t border-gece-cizgi py-3 ${open ? "px-4" : "px-3"}`}>
          <a href="#/account" aria-current={route.page === "account" ? "page" : undefined} title={open ? undefined : "Hesabım"} aria-label="Hesabım"
            className={`basilir mb-1 flex items-center gap-2.5 rounded-[10px] hover:bg-gece-2 hover:text-white ${open ? "px-2 py-1.5" : "w-10 h-10 justify-center"} ${
              route.page === "account" ? "bg-gece-2 text-white" : ""}`}>
            <Icon>{ACCOUNT_ICON}</Icon>
            {open && (
              <span className="min-w-0">
                <span className="block font-semibold text-[14px] text-white truncate">{auth?.fullName}</span>
                <span className="block text-[12px]">{isAdmin ? "Yönetici" : "Çalışan"} · Hesabım</span>
              </span>
            )}
          </a>
          {isAdmin && <NotificationButton expanded={open} className="mb-1" />}
          <div className={`flex gap-1 ${open ? "" : "flex-col items-center"}`}>
            <button onClick={toggle} aria-label={open ? "Menüyü daralt" : "Menüyü genişlet"} title={open ? "Menüyü daralt" : "Menüyü genişlet"}
              className="basilir w-10 h-10 rounded-[10px] grid place-items-center hover:bg-gece-2 hover:text-white">
              <Icon><path d={open ? "M15 6l-6 6 6 6" : "M9 6l6 6-6 6"} /></Icon>
            </button>
            <button onClick={logout} aria-label="Çıkış yap" title="Çıkış yap"
              className={`basilir h-10 rounded-[10px] flex items-center gap-2 hover:bg-gece-2 hover:text-white ${open ? "px-3 grow text-[14px] font-semibold" : "w-10 justify-center"}`}>
              <Icon><path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l5-5-5-5M15 12H4" /></Icon>{open && "Çıkış"}
            </button>
          </div>
        </div>
      </aside>

      <div className="grow min-w-0">
        {/* Telefon: ust cubuk */}
        <header className="md:hidden sticky top-0 z-20 bg-gece text-white h-14 px-4 flex items-center justify-between">
          <div className="flex items-center gap-2.5 min-w-0">
            <span className="w-8 h-8 rounded-lg bg-seri-1 grid place-items-center font-cond font-bold text-[14px] shrink-0">{initials}</span>
            <p className="font-cond font-bold text-[19px] tracking-tight truncate">{auth?.tenantName || "Galeri"}</p>
          </div>
          <div className="flex items-center gap-1 text-gece-yazi">
            {isAdmin && <NotificationButton expanded={false} />}
            <a href="#/account" aria-label="Hesabım" className={`basilir w-10 h-10 rounded-lg grid place-items-center hover:bg-gece-2 ${route.page === "account" ? "bg-gece-2 text-white" : ""}`}>
              <Icon>{ACCOUNT_ICON}</Icon>
            </a>
            <button onClick={logout} aria-label="Çıkış yap" className="basilir w-10 h-10 rounded-lg grid place-items-center hover:bg-gece-2">
              <Icon><path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l5-5-5-5M15 12H4" /></Icon>
            </button>
          </div>
        </header>

        <main className="px-4 md:px-8 py-5 md:py-8 pb-28 md:pb-10 max-w-[1240px] mx-auto">{content}</main>
      </div>

      {/* Telefon: alt sekmeler */}
      <nav className="md:hidden fixed bottom-0 inset-x-0 z-20 bg-yuzey border-t border-cizgi flex pb-[env(safe-area-inset-bottom)]">
        {tabs.map((s) => (
          <a key={s} href={`#/${s === "home" ? "" : s}`} aria-current={active === s ? "page" : undefined}
            className={`relative grow basis-0 min-w-0 flex flex-col items-center gap-0.5 pt-2.5 pb-2 text-[10.5px] font-semibold whitespace-nowrap ${active === s ? "text-plaka" : "text-murekkep-3"}`}>
            {active === s && <span className="absolute top-0 inset-x-4 h-[3px] rounded-b bg-plaka" />}
            <Icon>{ICON[s]}</Icon>{SHORT_LABEL[s]}
          </a>
        ))}
      </nav>
    </div>
  );
}

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="shrink-0">
      {children}
    </svg>
  );
}
