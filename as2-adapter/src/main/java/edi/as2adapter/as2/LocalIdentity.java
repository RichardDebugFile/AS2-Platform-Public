package edi.as2adapter.as2;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalIdentity {

    private final String as2Id;
    private final String receiptAddress;
    private final String path;
    private final String alias;
    private final String password;
    private As2Identity identity;

    public LocalIdentity(@Value("${as2.our-as2-id:PLATAFORMA}") String as2Id,
                         @Value("${as2.mdn-address:edi.plataforma@example.com}") String receiptAddress,
                         @Value("${as2.keystore-path:}") String path,
                         @Value("${as2.key-alias:}") String alias,
                         @Value("${as2.keystore-password:}") String password) {
        this.as2Id = as2Id;
        this.receiptAddress = receiptAddress;
        this.path = path;
        this.alias = alias;
        this.password = password;
    }

    public String as2Id() {
        return as2Id;
    }

    public String receiptAddress() {
        return receiptAddress;
    }

    public synchronized As2Identity get() {
        if (identity == null) {
            if (path.isBlank()) throw new As2Exception("Configure el almacén de claves AS2 de la plataforma");
            identity = As2Identity.fromPkcs12(Path.of(path), password.toCharArray(), alias);
        }
        return identity;
    }
}
