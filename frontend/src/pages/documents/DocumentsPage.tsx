import { Tabs } from "antd";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { Direction } from "../../service/documentsApi";
import { hasAnyRole, useAuthStore } from "../../store/authStore";
import RepositoryPanel from "./RepositoryPanel";
import X12GeneratorPanel from "./X12GeneratorPanel";
import X12Viewer from "./X12Viewer";

/**
 * Documentos EDI: repositorio de enviados y recibidos (HU-14) y visor X12 para todos los roles;
 * registrar documentos y el generador (HU-12) para quien edita; borrar, solo ADMIN.
 */
export default function DocumentsPage() {
    const { t } = useTranslation("documents");
    const user = useAuthStore((s) => s.user);
    const canEdit = hasAnyRole(user, ["ADMIN", "OPERATOR"]);
    const canDelete = hasAnyRole(user, ["ADMIN"]);
    const [tab, setTab] = useState("repository");
    const [text, setText] = useState("");
    const [pending, setPending] = useState<{ content: string; direction: Direction } | null>(null);

    const openInViewer = (content: string) => {
        setText(content);
        setTab("viewer");
    };

    const items = [
        {
            key: "repository",
            label: t("tabs.repository"),
            children: (
                <RepositoryPanel
                    canEdit={canEdit}
                    canDelete={canDelete}
                    onOpenInViewer={openInViewer}
                    pending={pending}
                    onPendingHandled={() => setPending(null)}
                />
            ),
        },
        { key: "viewer", label: t("tabs.viewer"), children: <X12Viewer text={text} onTextChange={setText} /> },
        ...(canEdit
            ? [
                  {
                      key: "generator",
                      label: t("tabs.generator"),
                      children: (
                          <X12GeneratorPanel
                              onOpenInViewer={openInViewer}
                              onSaveToRepository={(content) => {
                                  setPending({ content, direction: "OUTBOUND" });
                                  setTab("repository");
                              }}
                          />
                      ),
                  },
              ]
            : []),
    ];

    return <Tabs activeKey={tab} onChange={setTab} items={items} />;
}
