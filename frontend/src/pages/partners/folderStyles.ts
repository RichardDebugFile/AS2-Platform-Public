import type { FolderHealth, FolderState } from "../../store/partnersStore";

/** Color de la etiqueta de cada etapa y estado de carpetas (lista y panel del socio). */
export const STATE_COLOR: Record<FolderState, string> = { LOCAL: "default", SAMBA_LINKED: "blue", PRODUCTION: "green" };
export const HEALTH_COLOR: Record<FolderHealth, string> = { OK: "green", MISSING: "orange", NO_ACCESS: "orange", ERROR: "red" };
