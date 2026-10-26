// Tarayici bildirimleri: service worker kaydi, izin ve abonelik. Abonelik sunucuya kaydedilir
// (sadece ADMIN); galerinin sectigi saatte odeme gunu bildirimi gelir.
import { api } from "../../api/galeri";

export type PushStatus = "unsupported" | "off" | "on" | "blocked";

export function isPushSupported(): boolean {
  return "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
}

export function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) return;
  navigator.serviceWorker.register("/sw.js").catch(() => { /* bildirimsiz devam */ });
}

export async function getPushStatus(): Promise<PushStatus> {
  if (!isPushSupported()) return "unsupported";
  if (Notification.permission === "denied") return "blocked";
  const reg = await navigator.serviceWorker.getRegistration();
  const sub = await reg?.pushManager.getSubscription();
  return sub && Notification.permission === "granted" ? "on" : "off";
}

/** Izin ister, abone olur ve aboneligi sunucuya kaydeder. */
export async function enablePush(publicKey: string): Promise<PushStatus> {
  const permission = await Notification.requestPermission();
  if (permission !== "granted") return permission === "denied" ? "blocked" : "off";
  const reg = await navigator.serviceWorker.ready;
  const sub = (await reg.pushManager.getSubscription())
    ?? await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToUint8Array(publicKey) });
  const json = sub.toJSON();
  await api.subscribePush(sub.endpoint, json.keys?.p256dh ?? "", json.keys?.auth ?? "");
  return "on";
}

/** Bu cihazin aboneligini kapatir (cikista da cagrilir: ayni cihazda baska kullanici bildirim gormesin). */
export async function disablePush(): Promise<void> {
  if (!isPushSupported()) return;
  const reg = await navigator.serviceWorker.getRegistration();
  const sub = await reg?.pushManager.getSubscription();
  if (!sub) return;
  await api.unsubscribePush(sub.endpoint).catch(() => { /* sunucu sonraki gonderimde 410 ile siler */ });
  await sub.unsubscribe();
}

function urlBase64ToUint8Array(base64: string): Uint8Array<ArrayBuffer> {
  const padded = (base64 + "=".repeat((4 - (base64.length % 4)) % 4)).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(padded);
  const out = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}
