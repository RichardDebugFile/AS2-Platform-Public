import { color, palette } from "../../theme";

/**
 * Colores de resultado del tablero (estados del sistema de diseño): verde termino bien, rojo con
 * error, azul en curso y ambar repetido. Validados juntos para daltonismo y contraste sobre blanco.
 * Siempre van con su leyenda o etiqueta; el color nunca es la unica pista.
 */
export const outcomeColor = {
    OK: color.success,
    FAILED: color.error,
    IN_PROGRESS: palette.blue600,
    DUPLICATE: color.warning,
} as const;

export type Outcome = keyof typeof outcomeColor;
export const OUTCOMES: readonly Outcome[] = ["OK", "FAILED", "IN_PROGRESS", "DUPLICATE"];
