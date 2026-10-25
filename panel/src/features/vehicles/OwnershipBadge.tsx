// Konsinye ve ortakli araclari listede, kartta ve detayda belli eden kucuk rozet. Galerinin kendi aracinda gorunmez.
import { OWNERSHIP } from "../../shared/format";
import type { Ownership } from "../../shared/types";

const STYLE: Record<Exclude<Ownership, "DEALER">, string> = {
  CONSIGNMENT: "text-konsinye bg-konsinye-zemin",
  CO_OWNED: "text-ortakli bg-ortakli-zemin",
};

export function OwnershipBadge({ ownership, className = "" }: { ownership: Ownership | null | undefined; className?: string }) {
  if (!ownership || ownership === "DEALER") return null;
  return (
    <span className={`inline-flex items-center rounded px-1.5 py-0.5 text-[12px] font-semibold leading-none whitespace-nowrap ${STYLE[ownership]} ${className}`}>
      {OWNERSHIP[ownership]}
    </span>
  );
}
