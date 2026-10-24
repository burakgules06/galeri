// Basit hash yonlendirme: #/araclar, #/araclar/<id>, #/aylik, #/kisiler
import { useEffect, useState } from "react";

export type Rota =
  | { sayfa: "araclar" }
  | { sayfa: "arac"; id: string }
  | { sayfa: "aylik" }
  | { sayfa: "kisiler" };

function coz(hash: string): Rota {
  const parcalar = hash.replace(/^#\/?/, "").split("/").filter(Boolean);
  if (parcalar[0] === "araclar" && parcalar[1]) return { sayfa: "arac", id: parcalar[1] };
  if (parcalar[0] === "aylik") return { sayfa: "aylik" };
  if (parcalar[0] === "kisiler") return { sayfa: "kisiler" };
  return { sayfa: "araclar" };
}

export function git(yol: string) {
  window.location.hash = yol;
}

export function useRota(): Rota {
  const [rota, setRota] = useState<Rota>(() => coz(window.location.hash));
  useEffect(() => {
    const dinle = () => {
      setRota(coz(window.location.hash));
      window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", dinle);
    return () => window.removeEventListener("hashchange", dinle);
  }, []);
  return rota;
}
