package edi.alerts.smtp;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Lo que guarda el administrador. La contrasena es opcional: vacia conserva la guardada, y
 * {@code clearPassword} la borra. Sin usuario no se autentica y no se guarda contrasena.
 */
public record SmtpConfigRequest(
        @NotBlank @Size(max = 255)
        @Pattern(regexp = "[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?", message = "debe ser un nombre de servidor o una IP")
        String host,
        @NotNull @Min(1) @Max(65535) Integer port,
        @NotNull SmtpSecurity security,
        @Size(max = 255) String username,
        @Size(max = 255) String password,
        boolean clearPassword,
        @NotBlank @Email @Size(max = 255) String fromAddress,
        @Size(max = 120) String fromName) {
}
