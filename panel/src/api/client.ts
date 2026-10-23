// Tum API cagrilari buradan gecer: token ekler, GenericResponse zarfini acar,
// 401'de oturumu kapatir. Adres VITE_API_BASE'den gelir (lokalde bos -> Vite proxy).
import { ApiError } from "../shared/errors/ApiError";
import type { ApiEnvelope, LoginResponse } from "../shared/types";

const API_BASE = import.meta.env.VITE_API_BASE ?? "";
const AUTH_KEY = "galeri_auth";

let authToken: string | null = null;
let onSessionExpired: (() => void) | null = null;

export function setSessionExpiredHandler(handler: (() => void) | null) {
  onSessionExpired = handler;
}

export function saveAuth(auth: LoginResponse) {
  authToken = auth.token;
  localStorage.setItem(AUTH_KEY, JSON.stringify(auth));
}

export function loadAuth(): LoginResponse | null {
  const raw = localStorage.getItem(AUTH_KEY);
  if (!raw) return null;
  try {
    const auth = JSON.parse(raw) as LoginResponse;
    authToken = auth.token;
    return auth;
  } catch {
    return null;
  }
}

export function clearAuth() {
  authToken = null;
  localStorage.removeItem(AUTH_KEY);
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  if (authToken) headers["Authorization"] = `Bearer ${authToken}`;

  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, { ...options, headers });
  } catch {
    throw new ApiError(0, "Sunucuya ulaşılamadı");
  }

  const body = (await res.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (res.status === 401 && !path.startsWith("/api/auth/login")) {
    clearAuth();
    onSessionExpired?.();
    throw new ApiError(401, "Oturum süresi doldu");
  }
  if (!res.ok || !body || !body.success) {
    throw new ApiError(res.status, body?.message ?? "Beklenmeyen bir hata oluştu", body?.code ?? null);
  }
  return body.data;
}

export function login(phone: string, password: string) {
  return apiFetch<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ phone, password }),
  });
}
