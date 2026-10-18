// Ant Design 5 implementa sus metodos estaticos sobre ReactDOM.render, que React 19 elimino: sin
// este parche no fallan, simplemente no hacen nada. Debe importarse antes que antd.
import "@ant-design/v5-patch-for-react-19";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import AppProviders from "./AppProviders";
import "./i18n";
import "./index.css";

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <BrowserRouter>
            <AppProviders>
                <App />
            </AppProviders>
        </BrowserRouter>
    </StrictMode>,
);
