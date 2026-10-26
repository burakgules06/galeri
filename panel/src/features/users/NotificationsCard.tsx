import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { NotificationButton } from "../../layout/NotificationButton";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Field, inputClass } from "../../shared/ui/Common";

export function NotificationsCard() {
  const settings = useLoad(() => api.reminderSettings(), []);
  const [time, setTime] = useState<string | null>(null);
  const [busy, setBusy] = useState<"save" | "test" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const save = async (e: FormEvent) => {
    e.preventDefault();
    setBusy("save"); setError(null); setMessage(null);
    try {
      settings.setData(await api.updateReminderSettings(time ?? settings.data!.time));
      setMessage("Kaydedildi");
    } catch (err) { setError(errorMessage(err)); }
    finally { setBusy(null); }
  };
  const test = async () => {
    setBusy("test"); setError(null); setMessage(null);
    try { await api.testPush(); setMessage("Gönderildi"); }
    catch (err) { setError(errorMessage(err)); }
    finally { setBusy(null); }
  };
  return <section className="kart p-5 mb-4 space-y-3">
    <h2 className="etiket">Bildirimler</h2>
    <NotificationButton expanded surface="light" />
    <ErrorBox message={error ?? settings.error} />
    <form onSubmit={save} className="flex flex-wrap items-end gap-2">
      <Field label="Bildirim saati">
        <input type="time" step="60" className={`${inputClass} w-36`} value={time ?? settings.data?.time ?? ""}
          onChange={e => { setTime(e.target.value); setMessage(null); }} required disabled={!settings.data || busy !== null} />
      </Field>
      <Button type="submit" disabled={!settings.data || busy !== null}>{busy === "save" ? "Kaydediliyor…" : "Kaydet"}</Button>
    </form>
    <Button type="button" variant="secondary" onClick={test} disabled={busy !== null}>
      {busy === "test" ? "Gönderiliyor…" : "Test bildirimi gönder"}
    </Button>
    {message && <p role="status" className="text-sm font-semibold text-basari">{message}</p>}
  </section>;
}
