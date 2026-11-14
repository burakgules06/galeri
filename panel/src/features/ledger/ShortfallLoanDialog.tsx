// "Kimden borc alsin?" penceresi (useShortfallLoan): eksik tutar kadar borc, hesap ve tarih islemden gelir; degistirilebilir.
import { api } from "../../api/galeri";
import { tl } from "../../shared/format";
import { useLoad } from "../../shared/useLoad";
import { Button, ErrorBox, Modal } from "../../shared/ui/Common";
import { LoanForm } from "./LoanForm";
import type { LoanPrompt } from "./useShortfallLoan";

export function ShortfallLoanDialog({ prompt }: { prompt: LoanPrompt | null }) {
  if (!prompt) return null;
  return <Dialog key={`${prompt.shortfall.userId}-${prompt.shortfall.shortfallCents}`} prompt={prompt} />;
}

function Dialog({ prompt }: { prompt: LoanPrompt }) {
  const partners = useLoad(() => api.partners(), []);
  const { shortfall } = prompt;
  if (partners.error) {
    return (
      <Modal title="Kimden borç alsın?" open onClose={() => prompt.done(false)}
        footer={<Button variant="secondary" onClick={() => prompt.done(false)}>Kapat</Button>}>
        <ErrorBox message={partners.error} />
      </Modal>
    );
  }
  if (!partners.data) return null;
  return (
    <LoanForm mode="lend" partners={partners.data} borrowerId={shortfall.userId}
      shortfall={{
        cents: shortfall.shortfallCents,
        accountId: prompt.accountId,
        date: prompt.date,
        intro: <>
          <span className="font-semibold text-murekkep">{shortfall.name}</span> için{" "}
          <span className="sayi font-semibold text-tehlike">{tl(shortfall.shortfallCents)}</span> eksik.
          Borç verilince işlem kaldığı yerden devam eder; istemezsen Vazgeç'e bas.
        </>,
      }}
      onClose={() => prompt.done(false)} onSaved={() => prompt.done(true)} />
  );
}
