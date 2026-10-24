// Ara-sec kutusu: yazdikca kisileri suzer; listede yoksa "<ad> yeni kisi olarak ekle" ile aninda olusturur.
import { useMemo, useState } from "react";
import { api } from "../../api/galeri";
import type { ContactResponse, ContactSummary } from "../../shared/types";
import { inputClass } from "../../shared/ui/Common";

export function ContactPicker({ contacts, selected, onSelect, onCreated, placeholder }: {
  contacts: ContactResponse[];
  selected: ContactSummary | null;
  onSelect: (c: ContactSummary | null) => void;
  onCreated: (c: ContactResponse) => void;
  placeholder: string;
}) {
  const [text, setText] = useState("");
  const [open, setOpen] = useState(false);
  const [adding, setAdding] = useState(false);

  const results = useMemo(() => {
    const t = text.trim().toLocaleLowerCase("tr-TR");
    return (t ? contacts.filter((c) => c.name.toLocaleLowerCase("tr-TR").includes(t) || (c.phone ?? "").includes(t)) : contacts).slice(0, 8);
  }, [text, contacts]);

  if (selected) {
    return (
      <div className={`${inputClass} flex items-center justify-between`}>
        <span className="truncate">{selected.name}</span>
        <button type="button" className="text-[14px] text-plaka font-semibold" onClick={() => onSelect(null)}>Değiştir</button>
      </div>
    );
  }

  const addNew = async () => {
    const name = text.trim();
    if (!name) return;
    setAdding(true);
    try {
      const c = await api.addContact({ name, phone: null, type: "PERSON", notes: null });
      onCreated(c);
      onSelect({ id: c.id, name: c.name, phone: c.phone });
      setText("");
      setOpen(false);
    } finally {
      setAdding(false);
    }
  };

  const exactMatch = results.some((c) => c.name.toLocaleLowerCase("tr-TR") === text.trim().toLocaleLowerCase("tr-TR"));

  return (
    <div className="relative">
      <input
        className={inputClass}
        placeholder={placeholder}
        value={text}
        onChange={(e) => { setText(e.target.value); setOpen(true); }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
      />
      {open && (results.length > 0 || text.trim()) && (
        <ul className="absolute z-10 mt-1 w-full bg-yuzey border border-cizgi-2 rounded-lg shadow-lg max-h-64 overflow-auto py-1">
          {results.map((c) => (
            <li key={c.id}>
              <button type="button" className="w-full text-left px-3 py-2 hover:bg-yuzey-2"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => { onSelect({ id: c.id, name: c.name, phone: c.phone }); setText(""); setOpen(false); }}>
                <span className="font-medium">{c.name}</span>
                {c.phone && <span className="text-murekkep-3 text-[14px] ml-2">{c.phone}</span>}
              </button>
            </li>
          ))}
          {text.trim() && !exactMatch && (
            <li>
              <button type="button" disabled={adding} className="w-full text-left px-3 py-2 text-plaka font-semibold hover:bg-plaka-acik"
                onMouseDown={(e) => e.preventDefault()} onClick={addNew}>
                “{text.trim()}” yeni kişi olarak ekle
              </button>
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
