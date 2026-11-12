import { Tooltip, Typography } from "antd";
import { useTranslation } from "react-i18next";
import type { Dashboard } from "../../service/dashboardApi";
import { OUTCOMES, outcomeColor, type Outcome } from "./chartColors";

function countOf(d: Dashboard, o: Outcome): number {
    switch (o) {
        case "OK":
            return d.ok;
        case "FAILED":
            return d.failed;
        case "IN_PROGRESS":
            return d.inProgress;
        default:
            return d.duplicates;
    }
}

/** Una barra segmentada con el reparto de resultados y, debajo, la leyenda con cada cifra. */
export default function OutcomeBar({ data }: Readonly<{ data: Dashboard }>) {
    const { t, i18n } = useTranslation("common");
    const nf = new Intl.NumberFormat(i18n.language);
    const parts = OUTCOMES.map((o) => ({ o, n: countOf(data, o) }));
    const total = data.total;
    const pct = (n: number) => (total > 0 ? Math.round((n / total) * 1000) / 10 : 0);
    return (
        <div className="outcome">
            <div className="outcome-bar" role="img" aria-label={t("home.outcome.title")}>
                {parts
                    .filter((p) => p.n > 0)
                    .map((p) => (
                        <Tooltip key={p.o} title={`${t(`home.outcome.${p.o}`)}: ${nf.format(p.n)} (${nf.format(pct(p.n))} %)`}>
                            <span
                                className="outcome-seg"
                                style={{ flexGrow: p.n, background: outcomeColor[p.o] }}
                                data-testid={`outcome-${p.o}`}
                            />
                        </Tooltip>
                    ))}
            </div>
            <ul className="outcome-legend">
                {parts.map((p) => (
                    <li key={p.o}>
                        <span className="legend-swatch" style={{ background: outcomeColor[p.o] }} />
                        <Typography.Text>{t(`home.outcome.${p.o}`)}</Typography.Text>
                        <Typography.Text strong className="outcome-value">
                            {nf.format(p.n)}
                        </Typography.Text>
                        <Typography.Text type="secondary" className="outcome-pct">
                            {nf.format(pct(p.n))} %
                        </Typography.Text>
                    </li>
                ))}
            </ul>
        </div>
    );
}
