// Arac donanimlari (V36): gruplu cipler. Yonetici ve arac giren calisan duzenler; hazir listeden secilir, olmayan elle eklenir.
// Kayit listenin tamamini yazar; sunucu bosluklari temizler ve ayni adi bir kez tutar.
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { EQUIPMENT_GROUPS, groupEquipment, isPreset, sameEquipment } from "../../shared/equipment";
import { errorMessage } from "../../shared/useLoad";
import { Button, ErrorBox, inputClass, Modal } from "../../shared/ui/Common";

export function EquipmentCard({ vehicleId, equipment, editable, onSaved }: {
  vehicleId: string;
  equipment: string[];
  editable: boolean;
  onSaved: (equipment: string[]) => void;
}) {
  const [open, setOpen] = useState(false);
  const groups = groupEquipment(equipment);
  return (
    <section className="kart p-4 md:p-5">
      <div className="flex items-center justify-between gap-3 pb-3 mb-4 border-b border-cizgi">
        <h2 className="font-cond font-bold text-[21px] tracking-tight">Donanım</h2>
        {editable && <Button variant="secondary" className="h-9" onClick={() => setOpen(true)}>{equipment.length ? "Düzenle" : "Ekle"}</Button>}
      </div>
      {groups.length === 0 ? (
        <p className="text-[14px] text-murekkep-2">Donanım girilmemiş. Cam tavan, 360° kamera, ses sistemi gibi özellikler sahibinden açıklamasına ve vitrine eklenir.</p>
      ) : (
        <div className="space-y-3">
          {groups.map((g) => (
            <div key={g.title}>
              <p className="etiket mb-1.5">{g.title}</p>
              <ul className="flex flex-wrap gap-1.5">
                {g.items.map((i) => (
                  <li key={i} className="h-8 px-3 rounded-full border border-cizgi bg-yuzey-2 text-[14px] font-medium grid place-items-center">{i}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
      {open && <EquipmentModal vehicleId={vehicleId} initial={equipment} onClose={() => setOpen(false)}
        onSaved={(e) => { setOpen(false); onSaved(e); }} />}
    </section>
  );
}

function EquipmentModal({ vehicleId, initial, onClose, onSaved }: {
  vehicleId: string; initial: string[]; onClose: () => void; onSaved: (equipment: string[]) => void;
}) {
  const [selected, setSelected] = useState<string[]>(initial);
  const [custom, setCustom] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const has = (item: string) => selected.some((s) => sameEquipment(s, item));
  const toggle = (item: string) => setSelected((s) => has(item) ? s.filter((x) => !sameEquipment(x, item)) : [...s, item]);
  const customs = selected.filter((s) => !isPreset(s));

  const addCustom = (e?: FormEvent) => {
    e?.preventDefault();
    const name = custom.trim().replace(/\s+/g, " ");
    if (!name) return;
    if (name.length > 80) return setError("Donanım adı en fazla 80 karakter olabilir.");
    if (!has(name)) setSelected((s) => [...s, name]);
    setCustom("");
    setError(null);
  };

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      onSaved(await api.updateEquipment(vehicleId, selected));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="Donanım" open wide onClose={onClose}
      footer={<>
        <Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button>
        <Button type="button" onClick={save} disabled={saving}>{saving ? "Kaydediliyor…" : `Kaydet (${selected.length})`}</Button>
      </>}>
      <div className="space-y-4">
        <ErrorBox message={error} />
        {EQUIPMENT_GROUPS.map((g) => (
          <div key={g.title}>
            <p className="etiket mb-1.5">{g.title}</p>
            <div className="flex flex-wrap gap-1.5">
              {g.items.map((i) => <Chip key={i} label={i} on={has(i)} onClick={() => toggle(i)} />)}
            </div>
          </div>
        ))}
        <div>
          <p className="etiket mb-1.5">Listede yoksa</p>
          <form onSubmit={addCustom} className="flex gap-2">
            <input className={inputClass} value={custom} onChange={(e) => setCustom(e.target.value)} maxLength={80}
              placeholder="Örn. Burmester ses sistemi" />
            <Button type="submit" variant="secondary" disabled={!custom.trim()}>Ekle</Button>
          </form>
          {customs.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mt-2">
              {customs.map((c) => (
                <button key={c} type="button" onClick={() => toggle(c)} aria-label={`${c} kaldır`}
                  className="basilir h-9 pl-3 pr-2 rounded-full border text-[14px] font-semibold bg-murekkep text-white border-murekkep inline-flex items-center gap-1.5">
                  {c}
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}

function Chip({ label, on, onClick }: { label: string; on: boolean; onClick: () => void }) {
  return (
    <button type="button" role="checkbox" aria-checked={on} onClick={onClick}
      className={`basilir h-9 px-3 rounded-full border text-[14px] font-semibold whitespace-nowrap ${
        on ? "bg-murekkep text-white border-murekkep" : "bg-yuzey text-murekkep-2 border-cizgi-2 hover:bg-yuzey-2"}`}>
      {on && <span aria-hidden className="mr-1">✓</span>}{label}
    </button>
  );
}
