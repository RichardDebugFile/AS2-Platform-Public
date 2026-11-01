package edi.partners.dto;

import jakarta.validation.constraints.Size;

/** Lo unico editable de un certificado es su descripcion; los demas datos salen del certificado. */
public record CertificateUpdateRequest(@Size(max = 255) String description) {
}
