import type { FormInstance } from "antd";
import { useCallback, useEffect, useRef } from "react";

/** Espera tras la ultima tecla antes de buscar: evita una consulta por cada letra. */
export const SEARCH_DELAY_MS = 400;

/**
 * Busqueda mientras se escribe (AS2-105): cada cambio en los filtros envia el formulario despues de
 * {@link SEARCH_DELAY_MS}; si se sigue escribiendo, la espera se reinicia. Se usa en onValuesChange del
 * formulario; el boton Buscar y la tecla Enter siguen funcionando igual.
 */
export function useSearchAsYouType<T>(
    form: FormInstance<T>,
    delay = SEARCH_DELAY_MS,
): { schedule: () => void; cancel: () => void } {
    const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

    /** Descarta la busqueda pendiente (al pulsar Buscar o Limpiar, que consultan en el acto). */
    const cancel = useCallback(() => {
        if (timer.current) clearTimeout(timer.current);
        timer.current = null;
    }, []);

    const schedule = useCallback(() => {
        cancel();
        timer.current = setTimeout(() => {
            timer.current = null;
            form.submit();
        }, delay);
    }, [form, delay, cancel]);

    useEffect(() => cancel, [cancel]);

    return { schedule, cancel };
}
