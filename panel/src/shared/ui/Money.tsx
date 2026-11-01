// Kurusu kurusuna tutar: lira kismi buyuk, kurus ve "TL" kucuk ve soluk (1.250.000,50 TL). Eksi tutar "−" ile.
const lira = new Intl.NumberFormat("tr-TR", { maximumFractionDigits: 0 });

export function Money({ cents, className = "", compact = false }: { cents: number; className?: string; compact?: boolean }) {
  const abs = Math.abs(cents);
  const whole = Math.floor(abs / 100);
  const kurus = String(abs % 100).padStart(2, "0");
  return (
    <span className={`sayi whitespace-nowrap ${className}`}>
      {cents < 0 && "−"}{lira.format(whole)}
      <span className={`${compact ? "text-[0.8em]" : "text-[0.62em]"} font-semibold opacity-60 ml-[1px]`}>,{kurus} TL</span>
    </span>
  );
}
