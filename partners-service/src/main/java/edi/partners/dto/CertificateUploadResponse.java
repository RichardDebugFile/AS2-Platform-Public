package edi.partners.dto;

import java.util.List;

/**
 * Resultado de cargar un certificado.
 *
 * @param outcome  {@code CREATED} (nuevo, queda preferente), {@code ALREADY_ACTIVE} (ya estaba cargado y
 *                 activo: no cambia nada) o {@code REACTIVATED} (estaba inactivo: vuelve como respaldo)
 * @param warnings avisos para el operador, p. ej. demasiados certificados de cifrado activos
 */
public record CertificateUploadResponse(CertificateResponse certificate, Outcome outcome, List<String> warnings) {

    public enum Outcome { CREATED, ALREADY_ACTIVE, REACTIVATED }
}
