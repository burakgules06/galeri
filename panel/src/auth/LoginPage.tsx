import { useState, type FormEvent } from "react";
import { login as loginRequest } from "../api/client";
import { ApiError } from "../shared/errors/ApiError";
import { useAuth } from "./useAuth";

export function LoginPage() {
  const { login } = useAuth();
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      login(await loginRequest(phone, password));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Giriş yapılamadı");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center px-4">
      <form onSubmit={handleSubmit} className="w-full max-w-sm bg-yuzey rounded-2xl border border-cizgi p-6 space-y-4">
        <div>
          <h1 className="font-cond font-bold text-[30px] leading-none tracking-tight">Galeri</h1>
          <p className="text-murekkep-2 mt-1">Devam etmek için giriş yap</p>
        </div>

        <label className="block">
          <span className="text-[13px] font-semibold text-murekkep-2">Telefon</span>
          <input
            type="tel"
            autoComplete="username"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="5XX XXX XX XX"
            required
            className="mt-1 w-full rounded-lg border border-cizgi-2 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-plaka/20 focus:border-plaka"
          />
        </label>

        <label className="block">
          <span className="text-[13px] font-semibold text-murekkep-2">Şifre</span>
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            className="mt-1 w-full rounded-lg border border-cizgi-2 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-plaka/20 focus:border-plaka"
          />
        </label>

        {error && <p className="text-[14px] text-tehlike">{error}</p>}

        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-lg bg-plaka text-white h-11 font-semibold hover:bg-plaka-koyu basilir disabled:opacity-60"
        >
          {loading ? "Giriş yapılıyor..." : "Giriş yap"}
        </button>
      </form>
    </div>
  );
}
