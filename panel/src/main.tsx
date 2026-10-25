import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App.tsx";
import { AuthProvider } from "./auth/AuthProvider";
import { registerServiceWorker } from "./shared/push/push";
import { listenInstallPrompt } from "./shared/pwa/install";

registerServiceWorker();
listenInstallPrompt();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </StrictMode>,
);
