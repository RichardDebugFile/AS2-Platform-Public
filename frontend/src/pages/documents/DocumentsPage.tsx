import { Tabs } from "antd";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { hasAnyRole, useAuthStore } from "../../store/authStore";
import X12GeneratorPanel from "./X12GeneratorPanel";
import X12Viewer from "./X12Viewer";

/**
 * Documentos EDI (HU-12): visor X12 para todos los roles y generador para quien edita. El repositorio
 * de documentos enviados y recibidos llega con HU-14.
 */
export default function DocumentsPage() {
    const { t } = useTranslation("documents");
    const user = useAuthStore((s) => s.user);
    const canEdit = hasAnyRole(user, ["ADMIN", "OPERATOR"]);
    const [tab, setTab] = useState("viewer");
    const [text, setText] = useState("");

    const items = [
        { key: "viewer", label: t("tabs.viewer"), children: <X12Viewer text={text} onTextChange={setText} /> },
        ...(canEdit
            ? [
                  {
                      key: "generator",
                      label: t("tabs.generator"),
                      children: (
                          <X12GeneratorPanel
                              onOpenInViewer={(generated) => {
                                  setText(generated);
                                  setTab("viewer");
                              }}
                          />
                      ),
                  },
              ]
            : []),
    ];

    return <Tabs activeKey={tab} onChange={setTab} items={items} />;
}
