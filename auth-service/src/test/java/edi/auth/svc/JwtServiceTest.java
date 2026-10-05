package edi.auth.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final KeyService keys = new KeyService("", "");
    private final JwtService jwt = new JwtService(keys, "http://issuer", new SessionPolicy(5, 15, 60));

    @Test
    void accessToken_firmadoRs256ConClaimsEsperados() throws Exception {
        SignedJWT parsed = SignedJWT.parse(jwt.signAccessToken("alice", Set.of("ADMIN")));
        JWSVerifier verifier = new RSASSAVerifier(keys.getRsaJwk().toRSAPublicKey());

        assertThat(parsed.verify(verifier)).isTrue();
        assertThat(parsed.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getKeyID()).isEqualTo(keys.getRsaJwk().getKeyID());
        assertThat(parsed.getJWTClaimsSet().getSubject()).isEqualTo("alice");
        assertThat(parsed.getJWTClaimsSet().getIssuer()).isEqualTo("http://issuer");
        assertThat(parsed.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("ADMIN");
        long ttl = (parsed.getJWTClaimsSet().getExpirationTime().getTime()
                - parsed.getJWTClaimsSet().getIssueTime().getTime()) / 1000;
        assertThat(ttl).isEqualTo(300);
    }

    @Test
    void refreshToken_llevaTipoYCaducidadDeLaVentana() throws Exception {
        Instant exp = Instant.parse("2030-01-01T00:00:00Z");

        SignedJWT parsed = SignedJWT.parse(jwt.signRefreshToken("alice", exp));

        assertThat(parsed.getJWTClaimsSet().getStringClaim("typ")).isEqualTo("refresh");
        assertThat(parsed.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(exp);
    }

    @Test
    void tokenAlterado_noVerifica() throws Exception {
        String token = jwt.signAccessToken("alice", Set.of("OPERATOR"));
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1])).replace("OPERATOR", "ADMIN");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes())
                + "." + parts[2];

        SignedJWT parsed = SignedJWT.parse(forged);

        assertThat(parsed.verify(new RSASSAVerifier(keys.getRsaJwk().toRSAPublicKey()))).isFalse();
    }

    @Test
    void jwks_soloPublicaLaClavePublica() throws Exception {
        Map<String, Object> json = jwt.jwks();

        RSAKey published = (RSAKey) JWKSet.parse(json).getKeys().get(0);
        assertThat(published.isPrivate()).isFalse();
        assertThat(published.getKeyID()).isEqualTo(keys.getRsaJwk().getKeyID());
    }

    @Test
    void clavesPem_seCarganYElKidEsEstable() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String priv = pem("PRIVATE KEY", kp.getPrivate().getEncoded());
        String pub = pem("PUBLIC KEY", kp.getPublic().getEncoded());

        KeyService a = new KeyService(priv, pub);
        KeyService b = new KeyService(priv, pub);

        assertThat(a.getRsaJwk().toRSAPublicKey()).isEqualTo(kp.getPublic());
        assertThat(a.getRsaJwk().getKeyID()).isEqualTo(b.getRsaJwk().getKeyID());
    }

    @Test
    void soloUnaClavePem_impideArrancar() {
        assertThatThrownBy(() -> new KeyService("-----BEGIN PRIVATE KEY-----x", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_JWT_PRIVATE_KEY_PEM");
    }

    @Test
    void pemCorrupto_impideArrancar() {
        assertThatThrownBy(() -> new KeyService("no-es-pem", "tampoco"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder().encodeToString(der) + "\n-----END " + type
                + "-----\n";
    }
}
