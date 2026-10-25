// Kullanicilar: galerinin panel kullanicilari. Sadece yonetici. Kullanici silinmez, pasiflestirilir
// (alan/satan calisan kayitlari bozulmasin).
import { useState, type FormEvent } from "react";
import { api } from "../../api/galeri";
import { formatPhone } from "../../shared/format";
import type { UserResponse, UserRole } from "../../shared/types";
import { errorMessage, useLoad } from "../../shared/useLoad";
import { Button, Empty, ErrorBox, Field, inputClass, Modal, Segment } from "../../shared/ui/Common";
import { NumberInput } from "../../shared/ui/NumberInput";

const ROLE_OPTIONS: { value: UserRole; label: string }[] = [
  { value: "STAFF", label: "Çalışan" },
  { value: "ADMIN", label: "Yönetici" },
];

export function UsersPage() {
  const { data, error, reload } = useLoad(() => api.users(), []);
  const [editing, setEditing] = useState<UserResponse | "new" | null>(null);
  const [resetting, setResetting] = useState<UserResponse | null>(null);
  const active = data?.filter((u) => u.active) ?? [];
  const passive = data?.filter((u) => !u.active) ?? [];

  return (
    <div className="max-w-3xl">
      <div className="flex items-end justify-between gap-3 mb-4">
        <div>
          <p className="etiket">Panel erişimi</p>
          <h1 className="font-cond font-bold text-[30px] md:text-[34px] leading-tight tracking-tight">Kullanıcılar</h1>
        </div>
        <Button onClick={() => setEditing("new")}>Kullanıcı ekle</Button>
      </div>
      <p className="text-[14px] text-murekkep-2 mb-4">
        Yönetici para defterini, ödemeleri ve tutarları görür. Çalışan sadece araçları ve kendi alım-satımını görür, tutar görmez.
      </p>
      <ErrorBox message={error} />
      {data && data.length === 0 && <Empty title="Henüz kullanıcı yok" />}
      {active.length > 0 && <UserList users={active} onEdit={setEditing} onReset={setResetting} />}
      {passive.length > 0 && (
        <>
          <p className="etiket mt-6 mb-2">Pasif ({passive.length}) · giriş yapamaz</p>
          <UserList users={passive} onEdit={setEditing} onReset={setResetting} />
        </>
      )}
      {editing && (
        <UserForm existing={editing === "new" ? null : editing} onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); reload(); }} />
      )}
      {resetting && <ResetPasswordForm user={resetting} onClose={() => setResetting(null)} />}
    </div>
  );
}

function UserList({ users, onEdit, onReset }: { users: UserResponse[]; onEdit: (u: UserResponse) => void; onReset: (u: UserResponse) => void }) {
  return (
    <ul className="kart overflow-hidden divide-y divide-cizgi">
      {users.map((u) => (
        <li key={u.id} className={`flex flex-wrap items-center gap-x-3 gap-y-2 px-4 py-3 ${u.active ? "" : "bg-yuzey-2"}`}>
          <span className={`w-10 h-10 rounded-full grid place-items-center font-cond font-bold text-[15px] shrink-0 ${
            u.active ? "bg-plaka-acik text-plaka" : "bg-cizgi text-murekkep-3"}`}>{initials(u.fullName)}</span>
          <span className="grow min-w-0 basis-40">
            <span className="flex items-center gap-2 flex-wrap">
              <span className={`font-semibold ${u.active ? "" : "text-murekkep-2"}`}>{u.fullName || "İsimsiz"}</span>
              <RoleBadge role={u.role} active={u.active} />
              {u.self && <span className="text-[12px] font-semibold text-murekkep-3">(siz)</span>}
            </span>
            <span className="block sayi text-[15px] text-murekkep-2">{formatPhone(u.phone)}</span>
          </span>
          <span className="flex gap-1.5 ml-auto">
            <Button variant="secondary" className="h-9 px-3 text-[14px]" onClick={() => onReset(u)}>Şifre sıfırla</Button>
            <Button variant="secondary" className="h-9 px-3 text-[14px]" onClick={() => onEdit(u)}>Düzenle</Button>
          </span>
        </li>
      ))}
    </ul>
  );
}

function RoleBadge({ role, active }: { role: UserRole; active: boolean }) {
  if (!active) return <span className="text-[12px] font-semibold text-murekkep-2 bg-cizgi rounded px-1.5 py-0.5">Pasif</span>;
  return role === "ADMIN"
    ? <span className="text-[12px] font-semibold text-plaka bg-plaka-acik rounded px-1.5 py-0.5">Yönetici</span>
    : <span className="text-[12px] font-semibold text-murekkep-2 bg-yuzey-2 border border-cizgi rounded px-1.5 py-0.5">Çalışan</span>;
}

function initials(name: string | null): string {
  return (name || "?").split(" ").filter(Boolean).map((w) => w[0]).slice(0, 2).join("").toLocaleUpperCase("tr-TR");
}

function UserForm({ existing, onClose, onSaved }: { existing: UserResponse | null; onClose: () => void; onSaved: () => void }) {
  const [fullName, setFullName] = useState(existing?.fullName ?? "");
  const [phone, setPhone] = useState("");
  const [role, setRole] = useState<UserRole>(existing?.role ?? "STAFF");
  const [active, setActive] = useState(existing?.active ?? true);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const self = existing?.self ?? false;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await (existing
        ? api.updateUser(existing.id, { fullName, role, active })
        : api.addUser({ fullName, phone, role, password }));
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title={existing ? "Kullanıcıyı düzenle" : "Kullanıcı ekle"} open onClose={onClose}
      footer={<><Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button><Button type="submit" form="user-form" disabled={busy}>Kaydet</Button></>}>
      <form id="user-form" onSubmit={submit} className="space-y-3">
        <ErrorBox message={error} />
        <Field label="Ad soyad"><input className={inputClass} value={fullName} onChange={(e) => setFullName(e.target.value)} required maxLength={120} /></Field>
        {existing
          ? <Field label="Telefon (giriş)" hint="Telefon değiştirilemez; gerekirse yeni kullanıcı ekleyip bunu pasifleştirin.">
              <p className="sayi text-[18px] text-murekkep-2">{formatPhone(existing.phone)}</p>
            </Field>
          : <Field label="Cep telefonu (giriş için)">
              <NumberInput kind="phone" value={phone} onValueChange={setPhone} placeholder="0530 111 22 33" required />
            </Field>}
        <Field label="Rol" hint={self ? "Kendi rolünüzü düşüremezsiniz." : undefined}>
          {self ? <p className="font-semibold">Yönetici</p> : <Segment<UserRole> value={role} onChange={setRole} options={ROLE_OPTIONS} />}
        </Field>
        {existing && !self && (
          <Field label="Durum" hint={active ? undefined : "Pasif kullanıcı giriş yapamaz; açık oturumu da hemen kapanır."}>
            <Segment<"on" | "off"> value={active ? "on" : "off"} onChange={(v) => setActive(v === "on")}
              options={[{ value: "on", label: "Aktif" }, { value: "off", label: "Pasif" }]} />
          </Field>
        )}
        {!existing && (
          <Field label="İlk şifre" hint="En az 8 karakter. Kullanıcı girişten sonra Hesabım'dan değiştirebilir.">
            <input className={inputClass} type="password" autoComplete="new-password" value={password}
              onChange={(e) => setPassword(e.target.value)} minLength={8} maxLength={72} required />
          </Field>
        )}
      </form>
    </Modal>
  );
}

function ResetPasswordForm({ user, onClose }: { user: UserResponse; onClose: () => void }) {
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await api.resetUserPassword(user.id, password);
      setDone(true);
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <Modal title="Şifre sıfırla" open onClose={onClose}
      footer={done
        ? <Button type="button" onClick={onClose}>Tamam</Button>
        : <><Button variant="secondary" type="button" onClick={onClose}>Vazgeç</Button><Button type="submit" form="reset-form">Şifreyi değiştir</Button></>}>
      {done
        ? <p className="text-basari font-semibold">{user.fullName} için yeni şifre kaydedildi. Şifreyi kendisine iletin.</p>
        : (
          <form id="reset-form" onSubmit={submit} className="space-y-3">
            <ErrorBox message={error} />
            <p className="text-[14px] text-murekkep-2"><b className="text-murekkep">{user.fullName}</b> ({formatPhone(user.phone)}) için yeni şifre belirleyin.</p>
            <Field label="Yeni şifre" hint="En az 8 karakter">
              <input className={inputClass} type="password" autoComplete="new-password" value={password}
                onChange={(e) => setPassword(e.target.value)} minLength={8} maxLength={72} required autoFocus />
            </Field>
          </form>
        )}
    </Modal>
  );
}
