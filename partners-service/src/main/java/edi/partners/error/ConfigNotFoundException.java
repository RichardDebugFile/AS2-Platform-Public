package edi.partners.error;

import java.util.UUID;

public class ConfigNotFoundException extends RuntimeException {

    public ConfigNotFoundException(String config, UUID partnerId) {
        super("El socio " + partnerId + " no tiene configuracion de " + config);
    }
}
