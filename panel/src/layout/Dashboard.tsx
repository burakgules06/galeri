import { useAuth } from "../auth/useAuth";

// Iskelet: araclar, musteriler vb. moduller buraya sekme/menu olarak eklenecek.
export function Dashboard() {
  const { auth, logout } = useAuth();

  return (
    <div className="min-h-screen">
      <header className="bg-white border-b border-slate-200">
        <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between">
          <span className="font-semibold">{auth?.tenantName || "Galeri"}</span>
          <div className="flex items-center gap-3 text-sm">
            <span className="text-slate-500">{auth?.fullName}</span>
            <button onClick={logout} className="rounded-lg border border-slate-300 px-3 py-1 hover:bg-slate-50">
              Çıkış
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-4 py-8">
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center text-slate-500">
          Henüz modül yok. Araç stoku, müşteriler ve satışlar buraya gelecek.
        </div>
      </main>
    </div>
  );
}
