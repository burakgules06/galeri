import { LoginPage } from "./auth/LoginPage";
import { useAuth } from "./auth/useAuth";
import { Dashboard } from "./layout/Dashboard";

function App() {
  const { auth } = useAuth();
  return auth ? <Dashboard /> : <LoginPage />;
}

export default App;
