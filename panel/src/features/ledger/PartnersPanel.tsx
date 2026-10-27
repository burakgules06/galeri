// Ortaklar: dukkan sahibi ve sermayedarlar. Sermaye girisi / cekisi, kisiler arasi sermaye aktarimi, ortaklik tabani.
// Tutarlar /api/holders'tan: sermayedarinki defterden, dukkan sahibininki havuzdan kalan. Yuzde sadece bilgi.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { formatPhone, percent, tl, tlToCents, todayIso } from "../../shared/format";
import type { CapitalBase, CapitalDirection, ContactResponse, ContactSummary, Holder, Partner } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { ContactPicker } from "../contacts/ContactPicker";
import { PartnerCapitalCard } from "../home/PartnerCapitalCard";
import { NumberInput } from "../../shared/ui/NumberInput";
import { confirmOwnerCash } from "../../shared/ownerCash";

const BASES: { value: CapitalBase; label: string }[] = [
  { value: "ALL", label: "Tüm varlık" }, { value: "VEHICLES", label: "Araçlar" }, { value: "CASH", label: "Nakit" },
];

export function PartnersPanel({ onChange }: { onChange: () => void }) {
  const { data, error, reload } = useLoad(() => Promise.all([api.partners(), api.holders()]), []);
  const contacts = useLoad(() => api.contacts(), []);
  const [extra, setExtra] = useState<ContactResponse[]>([]);
  const [adding, setAdding] = useState<ContactSummary | null>(null);
  const [addBase, setAddBase] = useState<CapitalBase>("ALL");
  const [moving, setMoving] = useState<{ partner: Partner; direction: CapitalDirection } | null>(null);
  const [transferring, setTransferring] = useState(false);
  const [revision, setRevision] = useState(0);
  const [actionError, setActionError] = useState<string | null>(null);
  const partners = data?.[0] ?? [];
  const holders = data?.[1] ?? [];
  const holderOf = (p: Partner) => holders.find((h) => h.contactId === p.contactId);
  const changed = () => { reload(); setRevision((r) => r + 1); onChange(); };

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
      <PartnerCapitalCard revision={revision} />

      <section className="kart overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3 px-4 md:px-5 pt-4 pb-3 border-b border-cizgi">
          <h2 className="font-cond font-bold text-[21px] tracking-tight">Ortaklar</h2>
          <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto">
            <div className="grow sm:w-60">
              <ContactPicker contacts={[...extra, ...(contacts.data ?? [])]} selected={adding} onSelect={setAdding}
                onCreated={(c) => setExtra((l) => [c, ...l])} placeholder="Kişiyi ortak yap" />
            </div>
            <span className="w-36 shrink-0">
              <select className={inputClass} aria-label="Ortaklık tabanı" value={addBase} onChange={(e) => setAddBase(e.target.value as CapitalBase)}>
                {BASES.map((b) => <option key={b.value} value={b.value}>{b.label}</option>)}
              </select>
            </span>
            <Button disabled={!adding} onClick={() => adding && run(async () => { await api.addPartner(adding.id, addBase); setAdding(null); })}>Ekle</Button>
            {partners.length > 1 && <Button variant="secondary" onClick={() => setTransferring(true)}>Para aktar</Button>}
          </div>
        </div>
        {data && partners.length === 0 && <Empty title="Henüz ortak yok" description="Kişiyi ortak yapın, sonra sermaye girişi yapın." />}
        <ul className="divide-y divide-cizgi">
          {partners.map((p) => {
            const h = holderOf(p);
            return (
              <li key={p.contactId} className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 md:px-5 py-3">
                <span className="grow min-w-0 basis-48">
                  <span className="flex items-center gap-2 flex-wrap">
                    <a href={`#/contacts/${p.contactId}`} className="font-semibold hover:underline">{p.name}</a>
                    {p.owner && <span className="text-[12px] font-semibold text-plaka bg-plaka-acik rounded px-1.5 py-0.5">Dükkân sahibi</span>}
                  </span>
                  {p.phone && <span className="block sayi text-[14px] text-murekkep-3">{formatPhone(p.phone)}</span>}
                </span>
                <span className="text-right">
                  <span className="block sayi font-bold text-[20px] leading-tight">{tl(h?.capitalCents ?? p.capitalCents)}</span>
                  <span className="block text-[13px] text-murekkep-2">{percent(h?.shareBp ?? p.shareBp)}</span>
                </span>
                {!p.owner && (
                  <span className="w-36 shrink-0">
                    <select className={`${inputClass} h-9 text-[14px]`} aria-label="Ortaklık tabanı" value={h?.base ?? "ALL"}
                      onChange={(e) => run(() => api.setPartnerBase(p.contactId, e.target.value as CapitalBase))}>
                      {BASES.map((b) => <option key={b.value} value={b.value}>{b.label}</option>)}
                    </select>
                  </span>
                )}
                <span className="flex flex-wrap gap-1.5 justify-end w-full sm:w-auto">
                  <Button variant="secondary" className="h-9 px-3 text-[14px]" onClick={() => setMoving({ partner: p, direction: "IN" })}>Sermaye girişi</Button>
                  <Button variant="secondary" className="h-9 px-3 text-[14px]" disabled={!p.owner && (h?.cashCents ?? 0) <= 0}
                    onClick={() => setMoving({ partner: p, direction: "OUT" })}>Çekiş</Button>
                  {!p.owner && <Button variant="quiet" className="h-9 px-3 text-[14px]" onClick={() => run(() => api.setOwner(p.contactId))}>Sahip yap</Button>}
                </span>
              </li>
            );
          })}
        </ul>
      </section>

      {moving && <CapitalMovementForm partner={moving.partner} holder={holderOf(moving.partner)} direction={moving.direction}
        onClose={() => setMoving(null)} onSaved={() => { setMoving(null); changed(); }} />}
      {transferring && <TransferForm partners={partners} holders={holders} onClose={() => setTransferring(false)}
        onSaved={() => { setTransferring(false); changed(); }} />}
    </div>
  );
}

/** Sermayedar en fazla nakitteki parasini cekip aktarabilir; dukkan sahibi sinirsiz ama kasadaki payi biterse uyarilir. */
async function allowed(partner: Partner, holder: Holder | undefined, cents: number): Promise<string | null | false> {
  if (partner.owner) return (await confirmOwnerCash(cents)) ? null : false;
  const max = holder?.cashCents ?? 0;
  return cents > max ? `${partner.name} en fazla ${tl(Math.max(0, max))} verebilir (nakitteki parası).` : null;
}

function CapitalMovementForm({ partner, holder, direction, onClose, onSaved }: {
  partner: Partner; holder: Holder | undefined; direction: CapitalDirection; onClose: () => void; onSaved: () => void;
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
    if (!into) {
      const check = await allowed(partner, holder, cents);
      if (check === false) return;
      if (check) return setError(check);
    }
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
        <Field label="Tutar">
          <NumberInput className={`${inputClass} font-cond text-[18px]`} value={amount} onValueChange={setAmount}
            placeholder="1.000.000" autoFocus />
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

// Kisiler arasi aktarim: kasa degismez, sadece kimin parasi oldugu degisir.
function TransferForm({ partners, holders, onClose, onSaved }: {
  partners: Partner[]; holders: Holder[]; onClose: () => void; onSaved: () => void;
}) {
  const [from, setFrom] = useState(partners[0]?.contactId ?? "");
  const [to, setTo] = useState(partners[1]?.contactId ?? "");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(todayIso());
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const label = (p: Partner) => {
    const h = holders.find((x) => x.contactId === p.contactId);
    return `${p.name} · ${tl(h?.cashCents ?? 0)}`;
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const cents = tlToCents(amount);
    if (cents == null || Number.isNaN(cents) || cents <= 0) return setError("Tutarı 1.000.000 gibi yaz.");
    if (!from || !to || from === to) return setError("Gönderen ve alan farklı kişiler olmalı.");
    const sender = partners.find((p) => p.contactId === from)!;
    const check = await allowed(sender, holders.find((h) => h.contactId === from), cents);
    if (check === false) return;
    if (check) return setError(check);
    setSaving(true);
    setError(null);
    try {
      await api.capitalTransfer({ fromContactId: from, toContactId: to, date, amountCents: cents, description: description.trim() || null });
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="Para aktar" open onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="submit" form="transfer-capital-form" disabled={saving}>{saving ? "Kaydediliyor…" : "Aktar"}</Button>
      </>}>
      <form id="transfer-capital-form" onSubmit={submit} className="space-y-4">
        <ErrorBox message={error} />
        <div className="grid sm:grid-cols-2 gap-3">
          <Field label="Kimden">
            <select className={inputClass} value={from} onChange={(e) => setFrom(e.target.value)}>
              {partners.map((p) => <option key={p.contactId} value={p.contactId}>{label(p)}</option>)}
            </select>
          </Field>
          <Field label="Kime">
            <select className={inputClass} value={to} onChange={(e) => setTo(e.target.value)}>
              {partners.map((p) => <option key={p.contactId} value={p.contactId}>{label(p)}</option>)}
            </select>
          </Field>
        </div>
        <Field label="Tutar">
          <NumberInput className={`${inputClass} font-cond text-[18px]`} value={amount} onValueChange={setAmount} placeholder="1.000.000" autoFocus />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Tarih"><input type="date" className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} required /></Field>
          <Field label="Açıklama"><input className={inputClass} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="İsteğe bağlı" /></Field>
        </div>
      </form>
    </Modal>
  );
}
