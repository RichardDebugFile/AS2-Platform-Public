import { useAuthInitializer } from "./hooks/useAuthInitializer";
import AppRoutes from "./routes/AppRoutes";

export default function App() {
    useAuthInitializer();
    return <AppRoutes />;
}
