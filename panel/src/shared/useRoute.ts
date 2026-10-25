// Basit hash yonlendirme: #/ (ana ekran), #/vehicles, #/vehicles/<id>, #/payments, #/monthly,
// #/contacts, #/contacts/<id>, #/ledger, #/users, #/account
import { useEffect, useState } from "react";

export type Route =
  | { page: "home" }
  | { page: "vehicles" }
  | { page: "vehicle"; id: string }
  | { page: "payments" }
  | { page: "monthly" }
  | { page: "contacts" }
  | { page: "contact"; id: string }
  | { page: "ledger" }
  | { page: "users" }
  | { page: "account" };

function parse(hash: string): Route {
  const parts = hash.replace(/^#\/?/, "").split("/").filter(Boolean);
  if (parts[0] === "vehicles") return parts[1] ? { page: "vehicle", id: parts[1] } : { page: "vehicles" };
  if (parts[0] === "payments") return { page: "payments" };
  if (parts[0] === "monthly") return { page: "monthly" };
  if (parts[0] === "contacts") return parts[1] ? { page: "contact", id: parts[1] } : { page: "contacts" };
  if (parts[0] === "ledger") return { page: "ledger" };
  if (parts[0] === "users") return { page: "users" };
  if (parts[0] === "account") return { page: "account" };
  return { page: "home" };
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
