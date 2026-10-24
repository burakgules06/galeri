// Veri yukleme: { data, loading, error, reload }. Bagimliliklar degisince yeniden yukler.
// setState sadece promise sonucunda cagrilir (effect icinde senkron setState yok).
import { useCallback, useEffect, useState } from "react";
import { ApiError } from "./errors/ApiError";

export function errorMessage(e: unknown): string {
  return e instanceof ApiError ? e.message : "Beklenmeyen bir hata oluştu";
}

interface State<T> { key: string; data: T | null; error: string | null }

export function useLoad<T>(loader: () => Promise<T>, deps: unknown[]) {
  const [counter, setCounter] = useState(0);
  const key = JSON.stringify([...deps, counter]);
  const [state, setState] = useState<State<T>>({ key: "", data: null, error: null });

  useEffect(() => {
    let cancelled = false;
    loader()
      .then((v) => { if (!cancelled) setState({ key, data: v, error: null }); })
      .catch((e) => { if (!cancelled) setState((s) => ({ key, data: s.data, error: errorMessage(e) })); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  const reload = useCallback(() => setCounter((c) => c + 1), []);
  const setData = useCallback((v: T) => setState((s) => ({ ...s, data: v })), []);
  return { data: state.data, setData, loading: state.key !== key, error: state.error, reload };
}
