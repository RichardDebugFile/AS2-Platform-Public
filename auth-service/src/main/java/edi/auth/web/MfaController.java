package edi.auth.web;

import edi.auth.dto.MfaCodeRequest;
import edi.auth.dto.MfaSetupResponse;
import edi.auth.svc.MfaService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Segundo factor del usuario autenticado: estado, alta (setup + verify) y baja. */
@RestController
@RequestMapping("/users/me/mfa")
public class MfaController {

    private final MfaService mfa;

    public MfaController(MfaService mfa) {
        this.mfa = mfa;
    }

    @GetMapping("/status")
    public Map<String, Boolean> status(Authentication auth) {
        return Map.of("enabled", mfa.isEnabled(auth.getName()));
    }

    @PostMapping("/setup")
    public MfaSetupResponse setup(Authentication auth) {
        return mfa.setup(auth.getName());
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(Authentication auth, @RequestBody @Valid MfaCodeRequest req) {
        mfa.enable(auth.getName(), req.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(Authentication auth, @RequestBody @Valid MfaCodeRequest req) {
        mfa.disable(auth.getName(), req.code());
        return ResponseEntity.noContent().build();
    }
}
