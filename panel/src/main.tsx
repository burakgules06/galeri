import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App.tsx";
import { AuthProvider } from "./auth/AuthProvider";
import { registerServiceWorker } from "./shared/push/push";
import { listenInstallPrompt } from "./shared/pwa/install";

const root = createRoot(document.getElementById("root")!);

// Musteri vitrini (/vitrin/{slug}): girissiz ayri sayfa; panel ekranlari ve bildirim servisi calismaz
if (window.location.pathname.startsWith("/vitrin/")) {
  void import("./features/showroom/ShowroomApp").then(({ ShowroomApp }) => {
    root.render(<StrictMode><ShowroomApp /></StrictMode>);
  });
} else {
  registerServiceWorker();
  listenInstallPrompt();
  root.render(
    <StrictMode>
      <AuthProvider>
        <App />
      </AuthProvider>
    </StrictMode>,
  );
}
