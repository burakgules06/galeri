// Auth context ve hook, AuthProvider'dan ayri dosyada: component dosyalari sadece
// component export etsin (React Fast Refresh, eslint react-refresh/only-export-components).
import { createContext, useContext } from "react";
import type { LoginResponse } from "../shared/types";

export interface AuthContextValue {
  auth: LoginResponse | null;
  isAdmin: boolean;
  login: (data: LoginResponse) => void;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
