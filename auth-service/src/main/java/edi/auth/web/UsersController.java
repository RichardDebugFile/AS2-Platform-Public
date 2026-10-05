package edi.auth.web;

import edi.auth.dto.CreateUserRequest;
import edi.auth.dto.PageResponse;
import edi.auth.dto.UpdateActiveRequest;
import edi.auth.dto.UpdateRolesRequest;
import edi.auth.dto.UserResponse;
import edi.auth.svc.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administracion de usuarios. Las reglas por URL de SecurityConfig ya exigen ADMIN; el
 * {@code @PreAuthorize} es una segunda barrera por si una regla se mueve.
 */
@RestController
@RequestMapping("/users")
public class UsersController {

    private final UserService users;

    public UsersController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public UserResponse me(Authentication auth) {
        return users.me(auth.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(@RequestBody @Valid CreateUserRequest req) {
        return users.create(req);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> list(@RequestParam(name = "q", required = false) String q,
                                   @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                   Pageable pageable) {
        return PageResponse.of(users.search(q, pageable));
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateRoles(@PathVariable UUID id, @RequestBody @Valid UpdateRolesRequest req) {
        return users.updateRoles(id, req.roles());
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateActive(@PathVariable UUID id, @RequestBody @Valid UpdateActiveRequest req) {
        return users.updateActive(id, req.active());
    }
}
