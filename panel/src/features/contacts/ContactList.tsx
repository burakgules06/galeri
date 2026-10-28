// Musteriler, arac alinan kisiler ve kurumlar (Evim sirketi, banka). Sadece yonetici.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { formatPhone } from "../../shared/format";
import type { ContactResponse, ContactTag, ContactType } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";

export function ContactList() {
  const [q, setQ] = useState("");
  const [view, setView] = useState<"ALL" | ContactTag>("ALL");
  const { data, error, reload } = useLoad(() => api.contacts(q), [q]);
  const [editing, setEditing] = useState<ContactResponse | "new" | null>(null);
  const visible = (data ?? []).filter((c) => view === "ALL" || c.tags.includes(view));

  return (
    <div>
      <div className="flex items-end justify-between gap-3 mb-4">
        <div>
          <p className="etiket">Müşteri, satıcı, kurum</p>
          <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Kişiler</h1>
        </div>
        <Button onClick={() => setEditing("new")}>Kişi ekle</Button>
      </div>
      <input className={`${inputClass} md:max-w-xs mb-4`} placeholder="İsim ya da telefon ara" value={q}
        onChange={(e) => setQ(e.target.value)} aria-label="Kişi ara" />
      <div className="mb-4">
        <Segment<"ALL" | ContactTag> value={view} onChange={setView} options={[
          { value: "ALL", label: `Tüm kişiler${data ? ` (${data.length})` : ""}` },
          { value: "CUSTOMER", label: `Müşteriler${data ? ` (${data.filter((c) => c.tags.includes("CUSTOMER")).length})` : ""}` },
        ]} />
      </div>
      <ErrorBox message={error} />
      {data && visible.length === 0 && <Empty title={view === "CUSTOMER" ? "Henüz müşteri yok" : q ? "Aramaya uyan kişi yok" : "Henüz kişi yok"} description={view === "CUSTOMER" ? "Araç satışı tamamlanan alıcılar burada görünür." : "Araç alırken ya da satarken de isim yazarak ekleyebilirsin."} />}
      {visible.length > 0 && (
        <ul className="kart overflow-hidden divide-y divide-cizgi">
          {visible.map((c) => (
            <li key={c.id}>
              <a href={`#/contacts/${c.id}`} className="w-full text-left flex items-center gap-3 px-4 py-3 hover:bg-yuzey-2">
                <span className="grow min-w-0">
                  <span className="font-semibold">{c.name}</span>
                  {c.type === "ORGANIZATION" && <span className="ml-2 text-[12px] font-semibold text-plaka bg-plaka-acik rounded px-1.5 py-0.5">Kurum</span>}
                  {c.tags.map((tag) => <span key={tag} className="ml-2 text-[12px] font-semibold text-basari bg-basari-zemin rounded px-1.5 py-0.5">{tag === "CUSTOMER" ? "Müşteri" : tag}</span>)}
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
        <ContactForm existing={editing === "new" ? null : editing} onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); reload(); }} />
      )}
    </div>
  );
}

export function ContactForm({ existing, onClose, onSaved }: { existing: ContactResponse | null; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(existing?.name ?? "");
  const [phone, setPhone] = useState(formatPhone(existing?.phone));
  const [type, setType] = useState<ContactType>(existing?.type ?? "PERSON");
  const [notes, setNotes] = useState(existing?.notes ?? "");
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const request = { name, phone: phone.trim() || null, type, notes: notes.trim() || null };
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
          <Segment<ContactType> value={type} onChange={setType} options={[{ value: "PERSON", label: "Kişi" }, { value: "ORGANIZATION", label: "Kurum (Evim, banka)" }]} />
        </Field>
        <Field label="Ad soyad"><input className={inputClass} value={name} onChange={(e) => setName(e.target.value)} required /></Field>
        <Field label="Telefon"><NumberInput kind="phone" value={phone} onValueChange={setPhone} placeholder="0530 111 22 33" /></Field>
        <Field label="Notlar"><textarea className={`${inputClass} h-20 py-2`} value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
      </form>
    </Modal>
  );
}
