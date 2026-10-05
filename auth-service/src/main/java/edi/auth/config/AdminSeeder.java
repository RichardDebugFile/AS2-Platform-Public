package edi.auth.config;

import edi.auth.domain.Role;
import edi.auth.domain.UserAccount;
import edi.auth.repo.UserAccountRepository;
import edi.auth.svc.PasswordService;
import java.util.EnumSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el administrador inicial si no existe. Sin el no habria forma de entrar a la consola: el
 * auto-registro publico no existe y crear usuarios exige rol ADMIN.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    static final String DEFAULT_PASSWORD = "admin123";

    private final UserAccountRepository users;
    private final PasswordService passwords;
    private final String username;
    private final String password;
    private final String email;

    public AdminSeeder(
            UserAccountRepository users,
            PasswordService passwords,
            @Value("${seed.admin.username}") String username,
            @Value("${seed.admin.password}") String password,
            @Value("${seed.admin.email}") String email) {
        this.users = users;
        this.passwords = passwords;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByUsernameIgnoreCase(username)) {
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setId(UUID.randomUUID());
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash(passwords.encode(password));
        admin.setRoles(EnumSet.of(Role.ADMIN));
        admin.setActive(true);
        users.save(admin);
        if (DEFAULT_PASSWORD.equals(password)) {
            log.warn("Administrador '{}' creado con la contrasena por defecto: definir ADMIN_PASSWORD "
                    + "fuera de desarrollo", username);
        } else {
            log.info("Administrador '{}' creado", username);
        }
    }
}
