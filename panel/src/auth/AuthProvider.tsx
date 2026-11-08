// Oturum durumu: kayitli token'i ilk acilista yukler, 401 gelince oturumu kapatir.
import { useEffect, useState, type ReactNode } from "react";
import { clearAuth, loadAuth, saveAuth, setSessionExpiredHandler } from "../api/client";
import { api } from "../api/galeri";
import type { LoginResponse } from "../shared/types";
import { AuthContext, type AuthContextValue } from "./useAuth";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<LoginResponse | null>(() => loadAuth());

  useEffect(() => {
    setSessionExpiredHandler(() => setAuth(null));
    return () => setSessionExpiredHandler(null);
  }, []);

  // Calisanin alim-satim ve masraf yetkisi yonetici tarafindan sonradan acilip kapanabilir: her acilista tazelenir
  const staffToken = auth?.role === "STAFF" ? auth.token : null;
  useEffect(() => {
    if (!staffToken) return;
    let ignore = false;
    api.me().then((me) => {
      if (ignore) return;
      setAuth((a) => {
        if (!a || a.token !== staffToken || (a.canTrade === me.canTrade && a.canAddExpense === me.canAddExpense)) return a;
        const next = { ...a, canTrade: me.canTrade, canAddExpense: me.canAddExpense };
        saveAuth(next);
        return next;
      });
    }).catch(() => { /* 401 oturumu zaten kapatir */ });
    return () => { ignore = true; };
  }, [staffToken]);

  const value: AuthContextValue = {
    auth,
    isAdmin: auth?.role === "ADMIN" || auth?.role === "SUPER_ADMIN",
    isInvestor: auth?.role === "INVESTOR",
    canTrade: auth?.role === "STAFF" && !!auth.canTrade,
    canAddExpense: auth?.role === "STAFF" && !!auth.canAddExpense,
    login: (data) => {
      saveAuth(data);
      setAuth(data);
    },
    logout: () => {
      clearAuth();
      setAuth(null);
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
