package edi.auth.repo;

import edi.auth.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    /** Busqueda paginada por usuario o correo, sin distinguir mayusculas. {@code q} vacio = todos. */
    @Query("""
            select u from UserAccount u
            where :q = ''
               or lower(u.username) like lower(concat('%', :q, '%'))
               or lower(u.email) like lower(concat('%', :q, '%'))
            """)
    Page<UserAccount> search(@Param("q") String q, Pageable pageable);
}
