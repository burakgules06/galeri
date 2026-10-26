// Masraf kategorisi: en sik kullanilanlar hizli buton, yazdikca oneri. Listede yoksa kayitta o an olusur.
import { useMemo, useState } from "react";
import { api } from "../../api/galeri";
import type { ExpenseRoot } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { inputClass } from "../../shared/ui/Common";

const QUICK_COUNT = 5;

export function CategoryPicker({ root, name, onChange }: { root: ExpenseRoot; name: string; onChange: (name: string) => void }) {
  const { data } = useLoad(() => api.categories(root), [root]);
  const [open, setOpen] = useState(false);
  const categories = useMemo(() => data ?? [], [data]);
  const lower = (s: string) => s.trim().toLocaleLowerCase("tr-TR");

  const suggestions = useMemo(() => {
    const t = lower(name);
    return (t ? categories.filter((c) => lower(c.name).includes(t)) : categories).slice(0, 8);
  }, [name, categories]);
  const quick = categories.slice(0, QUICK_COUNT);
  const isNew = name.trim() !== "" && !categories.some((c) => lower(c.name) === lower(name));

  return (
    <div>
      {quick.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-2">
          {quick.map((c) => (
            <button key={c.id} type="button" onClick={() => onChange(c.name)}
              className={`basilir h-8 px-3 rounded-lg text-[14px] font-semibold border ${
                lower(c.name) === lower(name) ? "bg-murekkep text-white border-murekkep" : "bg-yuzey text-murekkep-2 border-cizgi-2 hover:bg-yuzey-2"}`}>
              {c.name}
            </button>
          ))}
        </div>
      )}
      <div className="relative">
        <input className={inputClass} value={name} placeholder={root === "VEHICLE" ? "Boya, ekspertiz, lastik…" : "Kira, fatura, maaş…"}
          onChange={(e) => { onChange(e.target.value); setOpen(true); }}
          onFocus={() => setOpen(true)} onBlur={() => setTimeout(() => setOpen(false), 150)} />
        {open && (suggestions.length > 0 || isNew) && (
          <ul className="absolute z-10 mt-1 w-full bg-yuzey border border-cizgi-2 rounded-lg shadow-lg max-h-64 overflow-auto py-1">
            {suggestions.map((c) => (
              <li key={c.id}>
                <button type="button" className="w-full text-left px-3 py-2 hover:bg-yuzey-2"
                  onMouseDown={(e) => e.preventDefault()} onClick={() => { onChange(c.name); setOpen(false); }}>
                  {c.name}
                </button>
              </li>
            ))}
            {isNew && (
              <li>
                <button type="button" className="w-full text-left px-3 py-2 text-plaka font-semibold hover:bg-plaka-acik"
                  onMouseDown={(e) => e.preventDefault()} onClick={() => { onChange(name.trim()); setOpen(false); }}>
                  “{name.trim()}” kategorisi olarak ekle
                </button>
              </li>
            )}
          </ul>
        )}
      </div>

    </div>
  );
}
