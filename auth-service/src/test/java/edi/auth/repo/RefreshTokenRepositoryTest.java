package edi.auth.repo;

import static org.assertj.core.api.Assertions.assertThat;

import edi.auth.domain.RefreshToken;
import edi.auth.domain.Role;
import edi.auth.domain.UserAccount;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

/**
 * Ejecuta de verdad las JPQL de los repositorios sobre H2, sin PostgreSQL: una errata en una
 * {@code @Query} se veria aqui y no al arrancar el servicio.
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:auth;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository tokens;

    @Autowired
    private UserAccountRepository users;

    private RefreshToken token(UUID userId, boolean revoked, Instant expiresAt) {
        RefreshToken rt = new RefreshToken();
        rt.setId(UUID.randomUUID());
        rt.setUserId(userId);
        rt.setTokenHash(UUID.randomUUID().toString().replace("-", ""));
        rt.setSessionStartedAt(Instant.now());
        rt.setExpiresAt(expiresAt);
        rt.setRevoked(revoked);
        return tokens.save(rt);
    }

    private RefreshToken token(UUID userId, boolean revoked) {
        return token(userId, revoked, Instant.now().plusSeconds(86_400));
    }

    @Test
    void revocarTodo_soloLasVivasDeEseUsuario() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        RefreshToken a1 = token(alice, false);
        token(alice, false);
        token(alice, true);
        RefreshToken b1 = token(bob, false);

        assertThat(tokens.revokeAllByUserId(alice)).isEqualTo(2);
        assertThat(tokens.findById(a1.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(tokens.findById(b1.getId()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void purga_borraSoloLasCaducadasAntesDelCorte() {
        UUID u = UUID.randomUUID();
        Instant corte = Instant.now().minusSeconds(86_400);
        token(u, false, corte.minusSeconds(60));
        token(u, true, corte.minusSeconds(60));
        RefreshToken reciente = token(u, false, corte.plusSeconds(60));

        assertThat(tokens.deleteExpiredBefore(corte)).isEqualTo(2);
        assertThat(tokens.findById(reciente.getId())).isPresent();
    }

    @Test
    void buscarPorHash() {
        RefreshToken rt = token(UUID.randomUUID(), false);

        assertThat(tokens.findByTokenHash(rt.getTokenHash())).isPresent();
        assertThat(tokens.findByTokenHash("otro")).isEmpty();
    }

    @Test
    void usuarios_busquedaPorNombreOCorreoSinMayusculas() {
        users.save(user("Operador1", "ops@example.com"));
        users.save(user("auditor1", "AUDIT@example.com"));

        PageRequest page = PageRequest.of(0, 10);
        assertThat(users.search("", page).getTotalElements()).isEqualTo(2);
        assertThat(users.search("OPER", page).getContent()).extracting(UserAccount::getUsername).containsExactly("Operador1");
        assertThat(users.search("audit@", page).getContent()).extracting(UserAccount::getUsername).containsExactly("auditor1");
        assertThat(users.findByUsernameIgnoreCase("operador1")).isPresent();
        assertThat(users.existsByUsernameIgnoreCase("AUDITOR1")).isTrue();
    }

    private static UserAccount user(String username, String email) {
        UserAccount ua = new UserAccount();
        ua.setId(UUID.randomUUID());
        ua.setUsername(username);
        ua.setEmail(email);
        ua.setPasswordHash("HASH");
        ua.setRoles(EnumSet.of(Role.OPERATOR));
        return ua;
    }

    /** Solo JPA: sin esto @DataJpaTest arrastraria la aplicacion entera (seeder incluido). */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaAuditing
    @EntityScan(basePackageClasses = RefreshToken.class)
    @EnableJpaRepositories(basePackageClasses = RefreshTokenRepository.class)
    static class SoloJpa {}
}
