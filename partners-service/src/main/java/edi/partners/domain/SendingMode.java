package edi.partners.domain;

/**
 * Envio automatico de la carpeta de salida: ninguno (solo por API), todos los archivos o solo los que
 * cumplen el filtro de nombre.
 */
public enum SendingMode {
    NONE,
    REGULAR,
    FILENAME_FILTER
}
