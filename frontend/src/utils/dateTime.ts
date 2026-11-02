/**
 * Fecha y hora con un solo formato en toda la consola ("2 nov 2026, 9:30:00"), en el idioma activo
 * y la zona horaria del navegador. Con el mes en letras no se confunden dia y mes entre idiomas.
 */
export function formatDateTime(iso: string, language: string): string {
    return new Intl.DateTimeFormat(language, { dateStyle: "medium", timeStyle: "medium" }).format(new Date(iso));
}
