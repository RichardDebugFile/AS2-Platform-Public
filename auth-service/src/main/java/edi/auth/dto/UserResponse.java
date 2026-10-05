package edi.auth.dto;

import edi.auth.domain.Role;
import edi.auth.domain.UserAccount;
import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String username, String email, Set<Role> roles, boolean active, boolean mfaEnabled) {

    public static UserResponse of(UserAccount ua) {
        return new UserResponse(ua.getId(), ua.getUsername(), ua.getEmail(), Set.copyOf(ua.getRoles()), ua.isActive(),
                ua.isMfaEnabled());
    }
}
