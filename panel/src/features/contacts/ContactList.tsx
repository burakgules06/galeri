// Kisiler: dukkanin disindaki taraflar. Ustte ana tur: Musteriler, Alis yaptiklarim (arac aldigimiz kisiler), Tedarikciler;
// altinda bicim: Tumu / Bireysel / Tuzel. Etiketler ("Araç sattık", "Araç aldık") islemlerden otomatik gelir. Sadece yonetici.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { formatPhone } from "../../shared/format";
import type { ContactKind, ContactResponse, ContactType } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";

const KINDS: { value: ContactKind; label: string; one: string }[] = [
  { value: "CUSTOMER", label: "Müşteriler", one: "Müşteri" },
  { value: "SELLER", label: "Alış yaptıklarım", one: "Alış yaptığım" },
  { value: "SUPPLIER", label: "Tedarikçiler", one: "Tedarikçi" },
];
type Form = "" | ContactType;

const TAG: Record<string, string> = { SOLD_TO: "Araç sattık", BOUGHT_FROM: "Araç aldık" };

export function ContactList() {
  const [q, setQ] = useState("");
  const [kind, setKind] = useState<ContactKind>("CUSTOMER");
  const [form, setForm] = useState<Form>("");
  const { data, error, reload } = useLoad(() => api.contacts(q), [q]);
  const [editing, setEditing] = useState<ContactResponse | "new" | null>(null);
  const all = data ?? [];
  const ofKind = all.filter((c) => (c.kind ?? "CUSTOMER") === kind);
  const list = form ? ofKind.filter((c) => c.type === form) : ofKind;
  const count = (k: ContactKind) => all.filter((c) => (c.kind ?? "CUSTOMER") === k).length;

  return (
    <div className="space-y-4">
      <div className="flex items-end justify-between gap-3">
        <div>
          <p className="etiket">Müşteri, alış yaptıklarım, tedarikçi</p>
          <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Kişiler</h1>
        </div>
        <Button onClick={() => setEditing("new")}>Kişi ekle</Button>
      </div>

      <div className="flex gap-1 rounded-[14px] bg-yuzey-2 p-1 w-full sm:w-fit max-w-full overflow-x-auto" role="tablist" aria-label="Ana tür">
        {KINDS.map((k) => (
          <button key={k.value} type="button" role="tab" aria-selected={kind === k.value} onClick={() => setKind(k.value)}
            className={`basilir shrink-0 grow sm:grow-0 h-10 px-4 rounded-[11px] text-[15px] font-semibold flex items-center justify-center gap-2 whitespace-nowrap ${
              kind === k.value ? "bg-yuzey text-murekkep shadow-sm" : "text-murekkep-2 hover:text-murekkep"}`}>
            {k.label}
            <span className="sayi text-[13px] rounded-full px-1.5 min-w-[22px] bg-cizgi text-murekkep-2">{count(k.value)}</span>
          </button>
        ))}
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center gap-2">
        <Segment<Form> value={form} onChange={setForm}
          options={[{ value: "", label: "Tümü" }, { value: "PERSON", label: "Bireysel" }, { value: "ORGANIZATION", label: "Tüzel" }]} />
        <input className={`${inputClass} sm:max-w-xs sm:ml-auto`} placeholder="İsim ya da telefon ara" value={q}
          onChange={(e) => setQ(e.target.value)} aria-label="Kişi ara" />
      </div>

      <ErrorBox message={error} />
      {data && list.length === 0 && (
        <Empty title={q ? "Aramaya uyan kişi yok" : "Bu listede kişi yok"} description="Araç alırken ya da satarken de isim yazarak ekleyebilirsin." />
      )}
      {list.length > 0 && (
        <ul className="kart overflow-hidden divide-y divide-cizgi">
          {list.map((c) => (
            <li key={c.id}>
              <a href={`#/contacts/${c.id}`} className="w-full text-left flex items-center gap-3 px-4 py-3 hover:bg-yuzey-2">
                <span className="grow min-w-0">
                  <span className="flex items-center gap-2 flex-wrap">
                    <span className="font-semibold">{c.name}</span>
                    <span className={`text-[12px] font-semibold rounded px-1.5 py-0.5 ${c.type === "ORGANIZATION" ? "text-plaka bg-plaka-acik" : "text-murekkep-2 bg-yuzey-2"}`}>
                      {c.type === "ORGANIZATION" ? "Tüzel" : "Bireysel"}
                    </span>
                    {(c.tags ?? []).map((t) => (
                      <span key={t} className="text-[12px] font-semibold rounded px-1.5 py-0.5 border border-cizgi text-murekkep-2">{TAG[t]}</span>
                    ))}
                  </span>
                  {c.notes && <span className="block text-[14px] text-murekkep-3 truncate">{c.notes}</span>}
                </span>
                <span className="sayi text-[16px] text-murekkep-2">{formatPhone(c.phone)}</span>
                <span className="text-murekkep-3" aria-hidden="true">›</span>
              </a>
            </li>
          ))}
        </ul>
      )}
      {editing && (
        <ContactForm existing={editing === "new" ? null : editing} defaultKind={kind} onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); reload(); }} />
      )}
    </div>
  );
}

export function ContactForm({ existing, defaultKind = "CUSTOMER", onClose, onSaved }: {
  existing: ContactResponse | null; defaultKind?: ContactKind; onClose: () => void; onSaved: () => void;
}) {
  const [name, setName] = useState(existing?.name ?? "");
  const [phone, setPhone] = useState(formatPhone(existing?.phone));
  const [kind, setKind] = useState<ContactKind>(existing?.kind ?? defaultKind);
  const [type, setType] = useState<ContactType>(existing?.type ?? "PERSON");
  const [notes, setNotes] = useState(existing?.notes ?? "");
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const request = { name, phone: phone.trim() || null, type, notes: notes.trim() || null, kind };
    try {
      await (existing ? api.updateContact(existing.id, request) : api.addContact(request));
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <Modal title={existing ? "Kişiyi düzenle" : "Kişi ekle"} open onClose={onClose}
      footer={<><Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button><Button type="submit" form="contact-form">Kaydet</Button></>}>
      <form id="contact-form" onSubmit={submit} className="space-y-3">
        <ErrorBox message={error} />
        <Field label="Tür">
          <Segment<ContactKind> value={kind} onChange={setKind} options={KINDS.map((k) => ({ value: k.value, label: k.one }))} />
        </Field>
        <Field label="Biçim">
          <Segment<ContactType> value={type} onChange={setType} options={[{ value: "PERSON", label: "Bireysel" }, { value: "ORGANIZATION", label: "Tüzel (şirket, Evim, banka)" }]} />
        </Field>
        <Field label={type === "ORGANIZATION" ? "Unvan" : "Ad soyad"}><input className={inputClass} value={name} onChange={(e) => setName(e.target.value)} required /></Field>
        <Field label="Telefon"><NumberInput kind="phone" value={phone} onValueChange={setPhone} placeholder="0530 111 22 33" /></Field>
        <Field label="Notlar"><textarea className={`${inputClass} h-20 py-2`} value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
      </form>
    </Modal>
  );
}
