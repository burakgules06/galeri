// Panelin telefona kurulmasi (PWA). Android/Chrome "beforeinstallprompt" olayini erken atar; main.tsx'te
// listenInstallPrompt() ile yakalanir, Hesabim'daki "Ana ekrana ekle" dugmesi sonra kullanir.
// iPhone'da kurulum dugmesi yok: Safari > Paylas > Ana Ekrana Ekle. Bildirimler iPhone'da sadece kurulu uygulamada calisir.

interface InstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

let deferred: InstallPromptEvent | null = null;
const listeners = new Set<() => void>();
const notify = () => listeners.forEach((l) => l());

export function listenInstallPrompt() {
  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();          // tarayicinin kendi bandi yerine Hesabim'daki dugme
    deferred = e as InstallPromptEvent;
    notify();
  });
  window.addEventListener("appinstalled", () => { deferred = null; notify(); });
}

export function onInstallChange(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function canPromptInstall(): boolean {
  return deferred !== null;
}

export async function promptInstall(): Promise<boolean> {
  if (!deferred) return false;
  const e = deferred;
  deferred = null;
  await e.prompt();
  const { outcome } = await e.userChoice;
  notify();
  return outcome === "accepted";
}

/** Ana ekrandan acilmis (kurulu) uygulama mi. */
export function isStandalone(): boolean {
  return window.matchMedia("(display-mode: standalone)").matches
    || (navigator as Navigator & { standalone?: boolean }).standalone === true;
}

export function isIos(): boolean {
  return /iPhone|iPad|iPod/.test(navigator.userAgent)
    || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);   // iPadOS masaustu kimligi
}
