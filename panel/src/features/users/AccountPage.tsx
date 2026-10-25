// Hesabim: herkes kendi sifresini degistirir. Yoneticiye Kullanicilar ekranina kisayol.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { useAuth } from "../../auth/useAuth";
import { errorMessage } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass } from "../../shared/ui/Common";
import { InstallCard } from "./InstallCard";

export function AccountPage() {
  const { auth, isAdmin } = useAuth();

  return (
    <div className="max-w-xl">
      <p className="etiket">{auth?.tenantName}</p>
      <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight mb-4">Hesabım</h1>

      <div className="kart p-5 mb-4 flex items-center gap-3">
        <div className="grow min-w-0">
          <p className="font-semibold text-[17px] truncate">{auth?.fullName}</p>
          <p className="text-[14px] text-murekkep-2">{isAdmin ? "Yönetici" : "Çalışan"}</p>
        </div>
        {isAdmin && <a href="#/users" className="basilir text-plaka font-semibold text-[15px] hover:underline shrink-0">Kullanıcıları yönet ›</a>}
      </div>

      <InstallCard />
      <ChangePasswordCard />
    </div>
  );
}

function ChangePasswordCard() {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [repeat, setRepeat] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);
  const mismatch = repeat.length > 0 && next !== repeat;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (next !== repeat) return;
    setBusy(true);
    setError(null);
    try {
      await api.changePassword(current, next);
      setDone(true);
      setCurrent(""); setNext(""); setRepeat("");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="kart p-5 space-y-3">
      <p className="etiket">Şifremi değiştir</p>
      <ErrorBox message={error} />
      {done && <p role="status" className="rounded-lg bg-basari-zemin text-basari text-[14px] font-semibold px-3 py-2">Şifreniz değiştirildi.</p>}
      <Field label="Mevcut şifre">
        <input className={inputClass} type="password" autoComplete="current-password" value={current}
          onChange={(e) => { setCurrent(e.target.value); setDone(false); }} required />
      </Field>
      <Field label="Yeni şifre" hint="En az 8 karakter">
        <input className={inputClass} type="password" autoComplete="new-password" value={next}
          onChange={(e) => setNext(e.target.value)} minLength={8} maxLength={72} required />
      </Field>
      <Field label="Yeni şifre (tekrar)" error={mismatch ? "Şifreler aynı değil" : null}>
        <input className={inputClass} type="password" autoComplete="new-password" value={repeat}
          onChange={(e) => setRepeat(e.target.value)} required />
      </Field>
      <div className="flex justify-end">
        <Button type="submit" disabled={busy || mismatch}>Şifreyi değiştir</Button>
      </div>
    </form>
  );
}
