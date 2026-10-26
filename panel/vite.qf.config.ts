import { mergeConfig } from "vite";
import base from "./vite.config";

export default mergeConfig(base, {
  server: { port: 5174, strictPort: true, proxy: { "/api": { target: "http://localhost:8083", changeOrigin: true } } },
});
