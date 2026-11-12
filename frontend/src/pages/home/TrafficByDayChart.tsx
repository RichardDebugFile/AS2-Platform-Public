import dayjs from "dayjs";
import { useTranslation } from "react-i18next";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { DayTraffic } from "../../service/dashboardApi";
import { color } from "../../theme";
import { outcomeColor } from "./chartColors";

const SERIES = [
    { key: "ok", fill: outcomeColor.OK },
    { key: "failed", fill: outcomeColor.FAILED },
    { key: "other", fill: outcomeColor.IN_PROGRESS },
] as const;

/** Columnas apiladas por dia: correctos, con error y el resto. */
export default function TrafficByDayChart({ days }: Readonly<{ days: DayTraffic[] }>) {
    const { t, i18n } = useTranslation("common");
    const nf = new Intl.NumberFormat(i18n.language);
    const short = (day: string) => dayjs(day).format(i18n.language.startsWith("en") ? "MM/DD" : "DD/MM");
    const long = (day: string) => dayjs(day).format("YYYY-MM-DD");
    return (
        <div className="chart-box" role="img" aria-label={t("home.byDay.title")}>
            <ResponsiveContainer width="100%" height={260}>
                <BarChart data={days} margin={{ top: 8, right: 8, bottom: 0, left: -16 }} barCategoryGap="30%">
                    <CartesianGrid vertical={false} stroke={color.border} />
                    <XAxis
                        dataKey="day"
                        tickFormatter={short}
                        tickLine={false}
                        axisLine={{ stroke: color.border }}
                        tick={{ fill: color.textSecondary, fontSize: 12 }}
                        minTickGap={12}
                    />
                    <YAxis
                        allowDecimals={false}
                        tickLine={false}
                        axisLine={false}
                        tick={{ fill: color.textSecondary, fontSize: 12 }}
                        tickFormatter={(v: number) => nf.format(v)}
                    />
                    <Tooltip
                        cursor={{ fill: color.bgSubtle }}
                        labelFormatter={(label) => long(String(label))}
                        formatter={(value, name) => [nf.format(Number(value)), t(`home.byDay.${String(name)}`)]}
                    />
                    <Legend
                        iconType="square"
                        itemSorter={(item) => SERIES.findIndex((x) => x.key === item.dataKey)}
                        formatter={(value: string) => (
                            <span style={{ color: color.textSecondary }}>{t(`home.byDay.${value}`)}</span>
                        )}
                    />
                    {SERIES.map((s, i) => (
                        <Bar
                            key={s.key}
                            dataKey={s.key}
                            stackId="day"
                            fill={s.fill}
                            stroke={color.bgSurface}
                            strokeWidth={2}
                            maxBarSize={24}
                            radius={i === SERIES.length - 1 ? [4, 4, 0, 0] : 0}
                            isAnimationActive={false}
                        />
                    ))}
                </BarChart>
            </ResponsiveContainer>
        </div>
    );
}
