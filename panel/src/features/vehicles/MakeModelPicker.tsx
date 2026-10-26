// Marka / model katalogdan: once marka, model listesi o markaya gore suzulur; paket / motor serbest yazilir
// (kayitta modelin devamina eklenir: "Passat" + "1.5 TSI Business"). "Listede yok" serbest yazmayi acar ve
// kataloga eklemeyi sorar (eklenmezse kaydedilemez). Katalog oncesi eski kayit dokunulmadikca aynen kalir.
import { useEffect, useState } from "react";
import { api } from "../../api/galeri";
import type { CatalogItem } from "../../shared/types";
import { useLoad } from "../../shared/useLoad";
import { Combobox } from "../../shared/ui/Combobox";
import { Field, inputClass } from "../../shared/ui/Common";
import type { MakeModelValue } from "./makeModel";

type Mode = "loading" | "catalog" | "free" | "legacy";

/** Kayitli modeli katalogdaki en uzun temel modelle ayirir: "Passat 1.5 TSI" -> ["Passat", "1.5 TSI"]. */
function split(model: string, models: CatalogItem[]): [string, string] | null {
  const m = model.trim().toLocaleLowerCase("tr-TR");
  let best: string | null = null;
  for (const c of models) {
    const k = c.name.toLocaleLowerCase("tr-TR");
    if ((m === k || m.startsWith(`${k} `)) && (!best || c.name.length > best.length)) best = c.name;
  }
  return best ? [best, model.trim().slice(best.length).trim()] : null;
}

export function MakeModelPicker({ initial, onChange }: { initial: { make: string; model: string } | null; onChange: (v: MakeModelValue) => void }) {
  const makes = useLoad(() => api.catalogMakes(), []);
  const [mode, setMode] = useState<Mode>(initial ? "loading" : "catalog");
  const [make, setMake] = useState(initial?.make ?? "");
  const [base, setBase] = useState("");
  const [trim, setTrim] = useState("");
  const [freeMake, setFreeMake] = useState(false);   // marka da listede yok
  const [addToCatalog, setAddToCatalog] = useState(true);
  const models = useLoad(() => (make && !freeMake ? api.catalogModels(make) : Promise.resolve([] as CatalogItem[])), [make, freeMake]);

  // Duzenleme: kayitli marka / modeli katalogla eslestir; eslesmezse eski kayit olarak goster
  useEffect(() => {
    if (!initial) return;
    let alive = true;
    Promise.all([api.catalogMakes(), api.catalogModels(initial.make).catch(() => [] as CatalogItem[])])
      .then(([allMakes, makeModels]) => {
        if (!alive) return;
        const catalogMake = allMakes.find((m) => m.name.toLocaleLowerCase("tr-TR") === initial.make.toLocaleLowerCase("tr-TR"));
        const parts = catalogMake ? split(initial.model, makeModels) : null;
        if (catalogMake && parts) {
          setMake(catalogMake.name);
          setBase(parts[0]);
          setTrim(parts[1]);
          setMode("catalog");
        } else {
          setMode("legacy");
        }
      })
      .catch(() => { if (alive) setMode("legacy"); });
    return () => { alive = false; };
  }, [initial]);

  // Ust forma bildir
  useEffect(() => {
    if (mode === "loading") return;
    if (mode === "legacy" && initial) {
      onChange({ make: initial.make, model: initial.model, addToCatalog: null, catalogModel: null, ready: true, needsCatalogConsent: false });
      return;
    }
    const full = [base.trim(), trim.trim()].filter(Boolean).join(" ");
    const free = mode === "free";
    onChange({
      make: make.trim(), model: full, ready: !!make.trim() && !!base.trim(),
      addToCatalog: free ? addToCatalog : null, catalogModel: free ? base.trim() : null, needsCatalogConsent: free && !addToCatalog,
    });
  }, [mode, make, base, trim, addToCatalog, initial, onChange]);

  const custom = (list: CatalogItem[] | null) => (v: string) => (list?.find((i) => i.name === v)?.custom ? "galeri eklemesi" : null);

  if (mode === "loading") return <p className="text-[14px] text-murekkep-3">Katalog yükleniyor…</p>;

  if (mode === "legacy" && initial) {
    return (
      <div className="rounded-[12px] border border-cizgi bg-yuzey-2 px-3 py-2.5 flex flex-wrap items-center justify-between gap-2">
        <span className="min-w-0">
          <span className="font-semibold">{initial.make}</span> <span className="text-murekkep-2">{initial.model}</span>
          <span className="block text-[12.5px] text-murekkep-3">Katalog öncesi kayıt; dokunmadıkça aynen kalır.</span>
        </span>
        <button type="button" className="text-[14px] font-semibold text-plaka" onClick={() => { setMake(""); setBase(""); setTrim(""); setMode("catalog"); }}>
          Katalogdan seç
        </button>
      </div>
    );
  }

  const free = mode === "free";
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <Field label="Marka">
          {free && freeMake
            ? <input className={inputClass} value={make} onChange={(e) => setMake(e.target.value)} placeholder="Marka adı" required maxLength={40} autoFocus />
            : <Combobox items={(makes.data ?? []).map((m) => m.name)} value={make} placeholder="Marka ara" ariaLabel="Marka"
                badge={custom(makes.data)}
                onChange={(v) => { setMake(v); setBase(""); setFreeMake(false); if (mode === "free") setMode("catalog"); }}
                footer="Listede yok" onFooter={() => { setMode("free"); setFreeMake(true); setMake(""); setBase(""); }} />}
        </Field>
        <Field label="Model">
          {free
            ? <input className={inputClass} value={base} onChange={(e) => setBase(e.target.value)} placeholder="Model adı (paketsiz)" required maxLength={60} autoFocus={!freeMake} />
            : <Combobox items={(models.data ?? []).map((m) => m.name)} value={base} onChange={setBase} disabled={!make}
                placeholder={make ? "Model ara" : "Önce marka"} ariaLabel="Model" badge={custom(models.data)}
                footer={make ? "Listede yok" : undefined} onFooter={() => { setMode("free"); setFreeMake(false); setBase(""); }} />}
        </Field>
      </div>
      <Field label="Paket / motor" hint="İsteğe bağlı, modelin devamına yazılır">
        <input className={inputClass} value={trim} onChange={(e) => setTrim(e.target.value)} placeholder="1.5 TSI Business" maxLength={50} />
      </Field>
      {free && (
        <div className="rounded-[12px] border border-uyari/40 bg-uyari-zemin/60 px-3 py-2.5 space-y-1.5">
          <p className="text-[14px] font-semibold">“{[make, base].filter(Boolean).join(" ") || "…"}” katalogda yok. Kataloğa eklensin mi?</p>
          <label className="flex items-center gap-2 text-[14px] cursor-pointer">
            <input type="checkbox" className="w-4 h-4 accent-plaka" checked={addToCatalog} onChange={(e) => setAddToCatalog(e.target.checked)} />
            Evet, bu galerinin kataloğuna ekle (sonraki araçlarda listede çıkar)
          </label>
          {!addToCatalog && <p className="text-[13px] text-tehlike">Kataloğa eklenmeden araç kaydedilemez.</p>}
          <button type="button" className="text-[13px] font-semibold text-plaka" onClick={() => { setMode("catalog"); setFreeMake(false); setMake(""); setBase(""); }}>
            Listeden seçmeye dön
          </button>
        </div>
      )}
    </div>
  );
}
