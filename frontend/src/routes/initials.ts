/** Iniciales para el avatar: dos letras del usuario (ana.operadora -> AO, admin -> AD). */
export function initials(username: string | undefined): string {
    if (!username) return "?";
    const parts = username.split(/[.\-_\s]+/).filter(Boolean);
    const letters = parts.length > 1 ? parts[0][0] + parts[1][0] : username.slice(0, 2);
    return letters.toUpperCase();
}
