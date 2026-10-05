package edi.auth.dto;

import edi.auth.domain.Role;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<Role> roles) {}
