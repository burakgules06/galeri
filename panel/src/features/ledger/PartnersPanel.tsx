// Ortaklar: sermaye ortaklari, paylari (sermaye hareketlerinden), dukkan sahibi, sermaye girisi / cekisi.
// Paylar hicbir yerde saklanmaz; her giris ve cekis bir harekettir ve paylari kendiliginden degistirir.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { formatPhone, percent, tl, tlToCents, todayIso } from "../../shared/format";
import type { CapitalDirection, ContactResponse, ContactSummary, Partner } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { ContactPicker } from "../contacts/ContactPicker";
import { CapitalCard } from "./CapitalCard";

export function PartnersPanel({ onChange }: { onChange: () => void }) {
  const { data, error, reload } = useLoad(() => api.partners(), []);
  const contacts = useLoad(() => api.contacts(), []);
  const [extra, setExtra] = useState<ContactResponse[]>([]);
  const [adding, setAdding] = useState<ContactSummary | null>(null);
  const [moving, setMoving] = useState<{ partner: Partner; direction: CapitalDirection } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const partners = data ?? [];
  const total = partners.reduce((t, p) => t + Math.max(0, p.capitalCents), 0);
  const changed = () => { reload(); onChange(); };

  const run = async (action: () => Promise<unknown>) => {
    setActionError(null);
    try {
      await action();
      changed();
    } catch (err) {
      setActionError(errorMessage(err));
    }
  };

  return (
    <div className="space-y-4">
      <ErrorBox message={error ?? actionError} />
      {partners.length > 0 && (
        <CapitalCard totalCents={total} partners={partners.filter((p) => p.capitalCents > 0).map((p) => ({
          contactId: p.contactId, name: p.name, owner: p.owner, capitalCents: p.capitalCents, shareBasisPoints: p.shareBp,
        }))} />
      )}

      <section className="kart overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3 px-4 md:px-5 pt-4 pb-3 border-b border-cizgi">
          <h2 className="font-cond font-bold text-[21px] tracking-tight">Sermaye ortakları</h2>
          <div className="flex items-center gap-2 w-full sm:w-auto">
            <div className="grow sm:w-64">
              <ContactPicker contacts={[...extra, ...(contacts.data ?? [])]} selected={adding} onSelect={setAdding}
                onCreated={(c) => setExtra((l) => [c, ...l])} placeholder="Kişiyi ortak yap" />
            </div>
            <Button disabled={!adding} onClick={() => adding && run(async () => { await api.addPartner(adding.id); setAdding(null); })}>Ekle</Button>
          </div>
        </div>
        {data && partners.length === 0 && <Empty title="Henüz ortak yok" description="Kişiyi ortak yapın, sonra sermaye girişi yapın." />}
        <ul className="divide-y divide-cizgi">
          {partners.map((p) => (
            <li key={p.contactId} className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 md:px-5 py-3">
              <span className="grow min-w-0 basis-48">
                <span className="flex items-center gap-2 flex-wrap">
                  <a href={`#/contacts/${p.contactId}`} className="font-semibold hover:underline">{p.name}</a>
                  {p.owner && <span className="text-[12px] font-semibold text-plaka bg-plaka-acik rounded px-1.5 py-0.5">Dükkân sahibi</span>}
                </span>
                {p.phone && <span className="block sayi text-[14px] text-murekkep-3">{formatPhone(p.phone)}</span>}
              </span>
              <span className="text-right">
                <span className="block sayi font-bold text-[20px] leading-tight">{tl(p.capitalCents)}</span>
                <span className="block text-[13px] text-murekkep-2">{percent(p.shareBp)} pay</span>
              </span>
              <span className="flex flex-wrap gap-1.5 justify-end w-full sm:w-auto">
                <Button variant="secondary" className="h-9 px-3 text-[14px]" onClick={() => setMoving({ partner: p, direction: "IN" })}>Sermaye girişi</Button>
                <Button variant="secondary" className="h-9 px-3 text-[14px]" disabled={p.capitalCents <= 0}
                  onClick={() => setMoving({ partner: p, direction: "OUT" })}>Çekiş</Button>
                {!p.owner && <Button variant="quiet" className="h-9 px-3 text-[14px]" onClick={() => run(() => api.setOwner(p.contactId))}>Sahip yap</Button>}
              </span>
            </li>
          ))}
        </ul>
      </section>

      {moving && <CapitalMovementForm partner={moving.partner} direction={moving.direction} onClose={() => setMoving(null)}
        onSaved={() => { setMoving(null); changed(); }} />}
    </div>
  );
}

function CapitalMovementForm({ partner, direction, onClose, onSaved }: {
  partner: Partner; direction: CapitalDirection; onClose: () => void; onSaved: () => void;
}) {
  const [amount, setAmount] = useState("");
  const [accountId, setAccountId] = useState("");
  const [date, setDate] = useState(todayIso());
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const accounts = useLoad(() => api.accounts(), []);
  const selected = accountId || accounts.data?.[0]?.id || "";
  const into = direction === "IN";

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const cents = tlToCents(amount);
    if (cents == null || Number.isNaN(cents) || cents <= 0) return setError("Tutarı 1.000.000 gibi yaz.");
    if (!into && cents > partner.capitalCents) return setError(`En fazla ${tl(partner.capitalCents)} çekilebilir.`);
    setSaving(true);
    setError(null);
    try {
      await api.moveCapital(partner.contactId, { direction, accountId: selected, date, amountCents: cents, description: description.trim() || null });
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={`${partner.name} · ${into ? "sermaye girişi" : "sermaye çekişi"}`} open onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="capital-form" disabled={saving}>{saving ? "Kaydediliyor…" : "Kaydet"}</Button>
      </>}>
      <form id="capital-form" onSubmit={submit} className="space-y-4">
        <ErrorBox message={error} />
        <p className="text-[14px] text-murekkep-2">
          Şu anki sermayesi <b className="sayi text-[16px] text-murekkep">{tl(partner.capitalCents)}</b> ({percent(partner.shareBp)}).
          {into ? " Para seçilen hesaba girer, ortağın payı artar." : " Para seçilen hesaptan çıkar, ortağın payı azalır."}
        </p>
        <Field label="Tutar" hint="TL">
          <input className={`${inputClass} font-cond text-[18px]`} value={amount} onChange={(e) => setAmount(e.target.value)}
            inputMode="decimal" placeholder="1.000.000" autoFocus />
        </Field>
        <Field label={into ? "Hangi hesaba girdi" : "Hangi hesaptan çıktı"}>
          {accounts.data && (
            <Segment<string> value={selected} onChange={setAccountId}
              options={accounts.data.map((a) => ({ value: a.id, label: `${a.name} · ${tl(a.balanceCents)}` }))} />
          )}
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Tarih"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} required /></Field>
          <Field label="Açıklama"><input className={inputClass} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="İsteğe bağlı" /></Field>
        </div>
      </form>
    </Modal>
  );
}
