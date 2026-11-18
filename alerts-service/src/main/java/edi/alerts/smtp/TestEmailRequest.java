package edi.alerts.smtp;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Destinatario del correo de prueba. */
public record TestEmailRequest(@NotBlank @Email @Size(max = 255) String to) {
}
