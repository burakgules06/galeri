// Basit hash yonlendirme: #/vehicles, #/vehicles/<id>, #/monthly, #/contacts, #/ledger
import { useEffect, useState } from "react";

export type Route =
  | { page: "vehicles" }
  | { page: "vehicle"; id: string }
  | { page: "monthly" }
  | { page: "contacts" }
  | { page: "ledger" };

function parse(hash: string): Route {
  const parts = hash.replace(/^#\/?/, "").split("/").filter(Boolean);
  if (parts[0] === "vehicles" && parts[1]) return { page: "vehicle", id: parts[1] };
  if (parts[0] === "monthly") return { page: "monthly" };
  if (parts[0] === "contacts") return { page: "contacts" };
  if (parts[0] === "ledger") return { page: "ledger" };
  return { page: "vehicles" };
}

export function navigate(path: string) {
  window.location.hash = path;
}

export function useRoute(): Route {
  const [route, setRoute] = useState<Route>(() => parse(window.location.hash));
  useEffect(() => {
    const onChange = () => {
      setRoute(parse(window.location.hash));
      window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  return route;
}
