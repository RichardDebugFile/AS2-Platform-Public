package edi.auth.svc;

import edi.auth.domain.UserAccount;
import edi.auth.dto.MfaSetupResponse;
import edi.auth.error.AuthException;
import edi.auth.error.MfaAlreadyEnabledException;
import edi.auth.repo.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta y baja del segundo factor por el propio usuario. */
@Service
public class MfaService {

    private final UserAccountRepository users;
    private final TotpService totp;
    private final CryptoService crypto;

    public MfaService(UserAccountRepository users, TotpService totp, CryptoService crypto) {
        this.users = users;
        this.totp = totp;
        this.crypto = crypto;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String username) {
        return find(username).isMfaEnabled();
    }

    /**
     * Genera un secreto nuevo, lo guarda cifrado y aun SIN activar: se activa en {@link #enable} al
     * confirmar el primer codigo. Con el MFA ya activo se rechaza; si no, cualquiera con la sesion
     * abierta lo apagaria sin conocer ningun codigo.
     */
    @Transactional
    public MfaSetupResponse setup(String username) {
        UserAccount ua = find(username);
        if (ua.isMfaEnabled()) {
            throw new MfaAlreadyEnabledException();
        }
        String secret = totp.generateBase32Secret();
        ua.setMfaSecret(crypto.encrypt(secret));
        String otpauth = totp.buildOtpAuthUrl(ua.getUsername(), secret);
        String masked = "****" + secret.substring(secret.length() - 4);
        return new MfaSetupResponse(otpauth, masked, totp.qrPngBase64(otpauth));
    }

    @Transactional
    public void enable(String username, String code) {
        UserAccount ua = find(username);
        if (ua.getMfaSecret() == null) {
            throw new IllegalArgumentException("Primero hay que generar el secreto (setup)");
        }
        if (!totp.verifyCode(crypto.decrypt(ua.getMfaSecret()), code)) {
            throw new IllegalArgumentException("Código inválido");
        }
        ua.setMfaEnabled(true);
    }

    /** Desactivar exige un codigo valido (RN-07); un alta a medias se descarta sin codigo. */
    @Transactional
    public void disable(String username, String code) {
        UserAccount ua = find(username);
        if (ua.isMfaEnabled() && !totp.verifyCode(crypto.decrypt(ua.getMfaSecret()), code)) {
            throw new IllegalArgumentException("Código inválido");
        }
        ua.setMfaEnabled(false);
        ua.setMfaSecret(null);
    }

    private UserAccount find(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(AuthException::accountGone);
    }
}
