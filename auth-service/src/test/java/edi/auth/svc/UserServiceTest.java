package edi.auth.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edi.auth.domain.Role;
import edi.auth.domain.UserAccount;
import edi.auth.dto.CreateUserRequest;
import edi.auth.dto.UserResponse;
import edi.auth.error.AuthException;
import edi.auth.error.UserNotFoundException;
import edi.auth.error.UsernameTakenException;
import edi.auth.repo.UserAccountRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class UserServiceTest {

    private UserAccountRepository repo;
    private PasswordService passwords;
    private RefreshTokenService refreshTokens;
    private UserService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserAccountRepository.class);
        passwords = mock(PasswordService.class);
        refreshTokens = mock(RefreshTokenService.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwords.encode(any())).thenReturn("HASH");
        service = new UserService(repo, passwords, refreshTokens);
    }

    private UserAccount existing(Role... roles) {
        UserAccount ua = new UserAccount();
        ua.setId(UUID.randomUUID());
        ua.setUsername("operador1");
        ua.setRoles(EnumSet.copyOf(Set.of(roles)));
        ua.setActive(true);
        when(repo.findById(ua.getId())).thenReturn(Optional.of(ua));
        return ua;
    }

    @Test
    void crear_sinRoles_asignaOperadorActivoYHasheaLaContrasena() {
        UserResponse r = service.create(new CreateUserRequest("nuevo", "password1", "n@example.com", null, null));

        assertThat(r.roles()).containsExactly(Role.OPERATOR);
        assertThat(r.active()).isTrue();
        verify(passwords).encode("password1");
    }

    @Test
    void crear_conRolesYDesactivado() {
        UserResponse r = service.create(
                new CreateUserRequest("aud", "password1", "a@example.com", Set.of(Role.AUDITOR), false));

        assertThat(r.roles()).containsExactly(Role.AUDITOR);
        assertThat(r.active()).isFalse();
    }

    @Test
    void crear_duplicadoSinDistinguirMayusculas_seRechaza() {
        when(repo.existsByUsernameIgnoreCase("Operador1")).thenReturn(true);
        CreateUserRequest req = new CreateUserRequest("Operador1", "password1", "o@example.com", null, null);

        assertThatThrownBy(() -> service.create(req)).isInstanceOf(UsernameTakenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void cambiarRoles_revocaSesionesSoloSiCambian() {
        UserAccount ua = existing(Role.OPERATOR);

        service.updateRoles(ua.getId(), Set.of(Role.OPERATOR));
        verify(refreshTokens, never()).revokeAllForUser(any());

        UserResponse r = service.updateRoles(ua.getId(), Set.of(Role.AUDITOR));
        assertThat(r.roles()).containsExactly(Role.AUDITOR);
        verify(refreshTokens).revokeAllForUser(ua.getId());
    }

    @Test
    void cambiarRoles_vacio_seRechaza() {
        UUID id = UUID.randomUUID();
        Set<Role> vacio = Set.of();

        assertThatThrownBy(() -> service.updateRoles(id, vacio)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cambiarRoles_usuarioInexistente() {
        UUID id = UUID.randomUUID();
        Set<Role> roles = Set.of(Role.ADMIN);

        assertThatThrownBy(() -> service.updateRoles(id, roles)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void desactivar_revocaSesiones_activarNo() {
        UserAccount ua = existing(Role.OPERATOR);

        service.updateActive(ua.getId(), true);
        verify(refreshTokens, never()).revokeAllForUser(any());

        UserResponse r = service.updateActive(ua.getId(), false);
        assertThat(r.active()).isFalse();
        verify(refreshTokens).revokeAllForUser(ua.getId());
    }

    @Test
    void me_cuentaInexistente_es401() {
        when(repo.findByUsernameIgnoreCase("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me("fantasma")).isInstanceOf(AuthException.class);
    }

    @Test
    void buscar_normalizaElFiltro() {
        UserAccount ua = existing(Role.ADMIN);
        PageRequest page = PageRequest.of(0, 20);
        when(repo.search(eq(""), any())).thenReturn(new PageImpl<>(List.of(ua)));

        assertThat(service.search(null, page).getContent()).extracting(UserResponse::username).containsExactly("operador1");
        verify(repo).search("", page);
    }
}
