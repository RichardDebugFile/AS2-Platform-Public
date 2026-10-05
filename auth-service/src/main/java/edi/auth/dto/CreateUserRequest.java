package edi.auth.dto;

import edi.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Alta de usuario por el administrador. Sin roles se asigna OPERATOR; sin {@code active}, activo. */
public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 80) String username,
        @NotBlank @Size(min = 8, max = 120) String password,
        @NotBlank @Email @Size(max = 180) String email,
        Set<Role> roles,
        Boolean active) {}
