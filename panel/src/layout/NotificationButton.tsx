// "Bildirimlere izin ver": sadece yoneticiye, tarayici destekliyorsa ve sunucuda VAPID anahtari varsa gorunur.
// Acikken tiklamak bu cihazin aboneligini kapatir.
import { useEffect, useState } from "react";
import { api } from "../api/galeri";
import { disablePush, enablePush, getPushStatus, isPushSupported, type PushStatus } from "../shared/push/push";

const TITLE: Record<Exclude<PushStatus, "unsupported">, string> = {
  off: "Bildirimlere izin ver",
  on: "Ödeme bildirimleri açık",
  blocked: "Bildirimler tarayıcıda engelli",
};

export function NotificationButton({ expanded, className = "" }: { expanded: boolean; className?: string }) {
  const [publicKey, setPublicKey] = useState("");
  const [status, setStatus] = useState<PushStatus>("unsupported");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!isPushSupported()) return;
    let alive = true;
    Promise.all([api.pushPublicKey(), getPushStatus()])
      .then(([k, s]) => { if (alive) { setPublicKey(k.publicKey); setStatus(s); } })
      .catch(() => { /* bildirim yoksa dugme gizli kalir */ });
    return () => { alive = false; };
  }, []);

  if (!publicKey || status === "unsupported") return null;

  const click = async () => {
    if (busy || status === "blocked") return;
    setBusy(true);
    try {
      if (status === "on") { await disablePush(); setStatus("off"); }
      else setStatus(await enablePush(publicKey));
    } catch {
      setStatus(await getPushStatus().catch(() => "off" as const));
    } finally {
      setBusy(false);
    }
  };

  const title = status === "blocked" ? `${TITLE.blocked} (adres çubuğundaki kilitten açabilirsiniz)` : status === "on" ? `${TITLE.on} (kapatmak için tıklayın)` : TITLE.off;

  return (
    <button onClick={click} disabled={busy} aria-label={title} title={title} aria-pressed={status === "on"}
      className={`basilir relative h-10 rounded-[10px] flex items-center gap-2 hover:bg-gece-2 hover:text-white disabled:opacity-60 ${
        expanded ? "px-3 w-full text-[14px] font-semibold" : "w-10 justify-center"} ${status === "blocked" ? "cursor-not-allowed" : ""} ${className}`}>
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="shrink-0">
        <path d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15zM10 20.5a2.2 2.2 0 0 0 4 0" />
        {status === "blocked" && <path d="M4 4l16 16" />}
      </svg>
      {status !== "blocked" && (
        <span className={`absolute ${expanded ? "left-[30px]" : "left-[24px]"} top-[9px] w-2 h-2 rounded-full ring-2 ring-gece ${status === "on" ? "bg-basari-canli" : "bg-uyari"}`} />
      )}
      {expanded && <span className="truncate">{TITLE[status]}</span>}
    </button>
  );
}
