package edi.auth.svc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Firma los JWT (RS256) y publica la clave publica en formato JWKS. */
// java.util.Date lo impone la API de Nimbus (JWTClaimsSet); se crea siempre desde Instant.
@Service
public class JwtService {

    private final RSAKey rsaKey;
    private final RSASSASigner signer;
    private final String issuer;
    private final long accessTtlSeconds;

    public JwtService(KeyService keys, @Value("${auth.issuer}") String issuer, SessionPolicy policy) {
        this.rsaKey = keys.getRsaJwk();
        this.issuer = issuer;
        this.accessTtlSeconds = policy.accessTtlSeconds();
        try {
            this.signer = new RSASSASigner(rsaKey.toPrivateKey());
        } catch (JOSEException e) {
            throw new IllegalStateException("Clave privada RSA no valida", e);
        }
    }

    public String signAccessToken(String subject, Set<String> roles) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(accessTtlSeconds)))
                .claim("roles", roles)
                .jwtID(UUID.randomUUID().toString())
                .build();
        return sign(claims);
    }

    /**
     * El {@code exp} del refresh es informativo: manda su fila en {@code refresh_token} (ver
     * {@link RefreshTokenService#validate}). Se firma con la misma caducidad para que coincidan.
     */
    public String signRefreshToken(String subject, Instant expiresAt) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expiresAt))
                .claim("typ", "refresh")
                .jwtID(UUID.randomUUID().toString())
                .build();
        return sign(claims);
    }

    private String sign(JWTClaimsSet claims) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.getKeyID())
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Error firmando JWT", e);
        }
        return jwt.serialize();
    }

    /** JWKS con SOLO la clave publica. */
    public Map<String, Object> jwks() {
        return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }
}
