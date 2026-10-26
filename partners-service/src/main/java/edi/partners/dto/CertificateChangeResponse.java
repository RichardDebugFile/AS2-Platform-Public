package edi.partners.dto;

import java.util.List;

/**
 * Resultado de marcar preferente, activar, desactivar o eliminar un certificado: como quedan los
 * certificados del mismo uso y los avisos (p. ej. que no queda ninguno activo).
 */
public record CertificateChangeResponse(List<CertificateResponse> sameUsage, List<String> warnings) {
}
