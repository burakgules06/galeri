// Oturum durumu: kayitli token'i ilk acilista yukler, 401 gelince oturumu kapatir.
import { useEffect, useState, type ReactNode } from "react";
import { clearAuth, loadAuth, saveAuth, setSessionExpiredHandler } from "../api/client";
import type { LoginResponse } from "../shared/types";
import { AuthContext, type AuthContextValue } from "./useAuth";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<LoginResponse | null>(() => loadAuth());

  useEffect(() => {
    setSessionExpiredHandler(() => setAuth(null));
    return () => setSessionExpiredHandler(null);
  }, []);

  const value: AuthContextValue = {
    auth,
    isAdmin: auth?.role === "ADMIN" || auth?.role === "SUPER_ADMIN",
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
