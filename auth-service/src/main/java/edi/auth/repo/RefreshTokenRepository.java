package edi.auth.repo;

import edi.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoca de golpe las sesiones vivas de un usuario (al desactivarlo o cambiarle los roles).
     *
     * <p>{@code clearAutomatically}: un update masivo no pasa por el contexto de persistencia; sin
     * limpiarlo, un RefreshToken ya cargado en la misma transaccion seguiria diciendo revoked=false.</p>
     *
     * @return cuantas sesiones se cortaron
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.userId = :userId and t.revoked = false")
    int revokeAllByUserId(@Param("userId") UUID userId);

    /** Purga periodica. Delete masivo en JPQL: el derivado de Spring Data borraria fila a fila. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
