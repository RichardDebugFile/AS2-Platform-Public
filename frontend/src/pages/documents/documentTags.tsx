import { Tag } from "antd";
import { useTranslation } from "react-i18next";
import { FAILED_STATUSES, type Direction, type DocumentStatus } from "../../service/documentsApi";

/** Estado de un documento: rojo si indica un problema, azul si espera algo, verde si termino bien. */
export function StatusTag({ status }: Readonly<{ status: DocumentStatus }>) {
    const { t } = useTranslation("documents");
    let color = "green";
    if (FAILED_STATUSES.has(status)) color = "red";
    else if (status === "MDN_PENDING" || status === "CREATED") color = "blue";
    else if (status === "DUPLICATE") color = "default";
    return <Tag color={color}>{t(`repository.status.${status}`, { defaultValue: status })}</Tag>;
}

export function DirectionTag({ direction }: Readonly<{ direction: Direction }>) {
    const { t } = useTranslation("documents");
    return <Tag color={direction === "INBOUND" ? "geekblue" : "purple"}>{t(`repository.direction.${direction}`)}</Tag>;
}
