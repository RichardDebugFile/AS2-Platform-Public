/**
 * Fecha y hora con un solo formato en toda la consola ("2 nov 2026, 21:30:00"), en el idioma activo
 * y la zona horaria del navegador. Con el mes en letras no se confunden dia y mes entre idiomas.
 * Se usa el idioma sin la region: "es-EC" (detectado del navegador) y "es" (elegido en el selector)
 * dan el mismo formato.
 */
export function formatDateTime(iso: string, language: string): string {
    const base = language.split("-")[0] || "es";
    return new Intl.DateTimeFormat(base, { dateStyle: "medium", timeStyle: "medium" }).format(new Date(iso));
}
