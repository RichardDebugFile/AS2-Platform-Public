package edi.auth.svc;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Hash BCrypt de contrasenas. */
@Service
public class PasswordService {

    private final PasswordEncoder encoder;

    public PasswordService(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    public String encode(String raw) {
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String hash) {
        return encoder.matches(raw, hash);
    }
}
