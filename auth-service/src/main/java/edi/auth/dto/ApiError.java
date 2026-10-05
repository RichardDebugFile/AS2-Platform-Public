package edi.auth.dto;

/** Cuerpo de error comun: la consola muestra {@code message} y decide por {@code code}. */
public record ApiError(int status, String code, String message) {}
