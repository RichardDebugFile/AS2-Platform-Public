package edi.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Entrada: carpeta donde se dejan los documentos recibidos del socio y perfil de interpretacion. */
@Entity
@Table(name = "incoming_config")
public class IncomingConfig extends PartnerConfig {

    @Column(name = "incoming_folder", nullable = false, length = 512)
    private String incomingFolder;

    @Column(name = "parser_profile", length = 128)
    private String parserProfile;

    protected IncomingConfig() {
    }

    public IncomingConfig(Partner partner) {
        super(partner);
    }

    public String getIncomingFolder() {
        return incomingFolder;
    }

    public void setIncomingFolder(String incomingFolder) {
        this.incomingFolder = incomingFolder;
    }

    public String getParserProfile() {
        return parserProfile;
    }

    public void setParserProfile(String parserProfile) {
        this.parserProfile = parserProfile;
    }
}
