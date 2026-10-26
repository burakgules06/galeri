// Ara-sec kutusu: yazdikca kisileri suzer. Listenin altinda her zaman "+ Yeni kişi / kurum" satiri var: ad yazilir,
// o turde kisi olusur ve hemen secilir; form kapanmaz. kind="ORGANIZATION" kurum secicidir (Evim, banka).
import { useMemo, useRef, useState, type KeyboardEvent } from "react";
import { api } from "../../api/galeri";
import { formatPhone } from "../../shared/format";
import type { ContactResponse, ContactSummary, ContactType } from "../../shared/types";
import { errorMessage } from "../../shared/useLoad";
import { inputClass } from "../../shared/ui/Common";

export function ContactPicker({ contacts, selected, onSelect, onCreated, placeholder, kind = "PERSON", className = inputClass }: {
  contacts: ContactResponse[];
  selected: ContactSummary | null;
  onSelect: (c: ContactSummary | null) => void;
  onCreated: (c: ContactResponse) => void;
  placeholder: string;
  kind?: ContactType;
  className?: string;
}) {
  const [text, setText] = useState("");
  const [open, setOpen] = useState(false);
  const [naming, setNaming] = useState(false);   // "+ Yeni" tiklandi: kutu yeni kaydin adini bekliyor
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const noun = kind === "ORGANIZATION" ? "kurum" : "kişi";

  const results = useMemo(() => {
    const t = text.trim().toLocaleLowerCase("tr-TR");
    return (t ? contacts.filter((c) => c.name.toLocaleLowerCase("tr-TR").includes(t) || (c.phone ?? "").includes(t)) : contacts).slice(0, 8);
  }, [text, contacts]);

  if (selected) {
    return (
      <div className={`${className} flex items-center justify-between gap-2`}>
        <span className="truncate">{selected.name}</span>
        <button type="button" className="text-[14px] text-plaka font-semibold shrink-0" onClick={() => onSelect(null)}>Değiştir</button>
      </div>
    );
  }

  const choose = (c: ContactSummary) => {
    onSelect({ id: c.id, name: c.name, phone: c.phone });
    setText("");
    setOpen(false);
    setNaming(false);
  };

  const addNew = async () => {
    const name = text.trim();
    if (!name) {
      setNaming(true);
      input.current?.focus();
      return;
    }
    setAdding(true);
    setError(null);
    try {
      const c = await api.addContact({ name, phone: null, type: kind, notes: null });
      onCreated(c);
      choose(c);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setAdding(false);
    }
  };

  const exactMatch = results.find((c) => c.name.toLocaleLowerCase("tr-TR") === text.trim().toLocaleLowerCase("tr-TR"));

  // Enter formu gondermesin: tam eslesme varsa onu sec, yoksa yeni kayit olustur
  const keyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key !== "Enter" || !text.trim()) return;
    e.preventDefault();
    if (exactMatch) choose(exactMatch);
    else void addNew();
  };

  return (
    <div className="relative">
      <input
        ref={input}
        className={className}
        placeholder={naming ? `Yeni ${noun}nin adını yaz` : placeholder}
        value={text}
        onChange={(e) => { setText(e.target.value); setOpen(true); }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => { setOpen(false); setNaming(false); }, 150)}
        onKeyDown={keyDown}
      />
      {open && (
        <ul className="absolute z-10 mt-1 w-full bg-yuzey border border-cizgi-2 rounded-lg shadow-lg max-h-64 overflow-auto py-1">
          {!naming && results.map((c) => (
            <li key={c.id}>
              <button type="button" className="w-full text-left px-3 py-2 hover:bg-yuzey-2"
                onMouseDown={(e) => e.preventDefault()} onClick={() => choose(c)}>
                <span className="font-medium">{c.name}</span>
                {c.phone && <span className="text-murekkep-3 text-[14px] ml-2">{formatPhone(c.phone)}</span>}
              </button>
            </li>
          ))}
          {!exactMatch && (
            <li className={!naming && results.length > 0 ? "border-t border-cizgi mt-1 pt-1" : ""}>
              <button type="button" disabled={adding} className="w-full text-left px-3 py-2 text-plaka font-semibold hover:bg-plaka-acik disabled:opacity-60"
                onMouseDown={(e) => e.preventDefault()} onClick={() => void addNew()}>
                {text.trim() ? `+ “${text.trim()}” yeni ${noun} olarak ekle` : naming ? `Adı yazın, Enter ile ekleyin` : `+ Yeni ${noun}`}
              </button>
            </li>
          )}
        </ul>
      )}
      {error && <p className="text-[13px] text-tehlike mt-1">{error}</p>}
    </div>
  );
}
