package edi.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Acuses (MDN): donde se guardan, si se piden firmados y si llegan en la misma conexion o despues. */
@Entity
@Table(name = "mdn_config")
public class MdnConfig extends PartnerConfig {

    @Column(name = "local_folder", nullable = false, length = 512)
    private String localFolder;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_type", nullable = false, length = 8)
    private MdnReceiptType receiptType;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false, length = 8)
    private MdnMode deliveryMode;

    @Column(name = "async_mdn_url", length = 1024)
    private String asyncMdnUrl;

    protected MdnConfig() {
    }

    public MdnConfig(Partner partner) {
        super(partner);
    }

    public String getLocalFolder() {
        return localFolder;
    }

    public void setLocalFolder(String localFolder) {
        this.localFolder = localFolder;
    }

    public MdnReceiptType getReceiptType() {
        return receiptType;
    }

    public void setReceiptType(MdnReceiptType receiptType) {
        this.receiptType = receiptType;
    }

    public MdnMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(MdnMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getAsyncMdnUrl() {
        return asyncMdnUrl;
    }

    public void setAsyncMdnUrl(String asyncMdnUrl) {
        this.asyncMdnUrl = asyncMdnUrl;
    }
}
