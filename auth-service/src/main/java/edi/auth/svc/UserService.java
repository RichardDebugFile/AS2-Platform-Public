package edi.auth.svc;

import edi.auth.domain.Role;
import edi.auth.domain.UserAccount;
import edi.auth.dto.CreateUserRequest;
import edi.auth.dto.UserResponse;
import edi.auth.error.AuthException;
import edi.auth.error.UserNotFoundException;
import edi.auth.error.UsernameTakenException;
import edi.auth.repo.UserAccountRepository;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administracion de cuentas: alta, roles y activacion. */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserAccountRepository repo;
    private final PasswordService passwords;
    private final RefreshTokenService refreshTokens;

    public UserService(UserAccountRepository repo, PasswordService passwords, RefreshTokenService refreshTokens) {
        this.repo = repo;
        this.passwords = passwords;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public UserResponse create(CreateUserRequest req) {
        if (repo.existsByUsernameIgnoreCase(req.username())) {
            throw new UsernameTakenException(req.username());
        }
        UserAccount ua = new UserAccount();
        ua.setId(UUID.randomUUID());
        ua.setUsername(req.username());
        ua.setEmail(req.email());
        ua.setPasswordHash(passwords.encode(req.password()));
        ua.setRoles(req.roles() == null || req.roles().isEmpty() ? EnumSet.of(Role.OPERATOR) : EnumSet.copyOf(req.roles()));
        ua.setActive(req.active() == null || req.active());
        return UserResponse.of(repo.save(ua));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(String q, Pageable pageable) {
        return repo.search(q == null ? "" : q.trim(), pageable).map(UserResponse::of);
    }

    @Transactional(readOnly = true)
    public UserResponse me(String username) {
        return repo.findByUsernameIgnoreCase(username)
                .map(UserResponse::of)
                .orElseThrow(AuthException::accountGone);
    }

    /**
     * Reemplaza los roles. Si cambian de verdad se revocan sus sesiones: los roles viajan dentro del
     * access token y, sin revocar, seguiria renovando con los permisos viejos. Si no cambian no se
     * revoca nada, para no echar al usuario cuando se acepta el dialogo sin tocarlo.
     */
    @Transactional
    public UserResponse updateRoles(UUID id, Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Debe asignarse al menos un rol");
        }
        UserAccount ua = repo.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        EnumSet<Role> nuevos = EnumSet.copyOf(roles);
        if (!nuevos.equals(ua.getRoles())) {
            ua.setRoles(nuevos);
            int cortadas = refreshTokens.revokeAllForUser(id);
            log.info("Roles de {} cambiados a {}: {} sesion(es) revocada(s)", ua.getUsername(), nuevos, cortadas);
        }
        return UserResponse.of(ua);
    }

    /**
     * Activa o desactiva. Al desactivar se revocan sus sesiones: la renovacion se corta ya y el
     * access token vigente deja de servir como maximo al caducar ({@code auth.access-ttl-min}).
     */
    @Transactional
    public UserResponse updateActive(UUID id, boolean active) {
        UserAccount ua = repo.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        ua.setActive(active);
        if (!active) {
            int cortadas = refreshTokens.revokeAllForUser(id);
            log.info("Usuario {} desactivado: {} sesion(es) revocada(s)", ua.getUsername(), cortadas);
        }
        return UserResponse.of(ua);
    }
}
