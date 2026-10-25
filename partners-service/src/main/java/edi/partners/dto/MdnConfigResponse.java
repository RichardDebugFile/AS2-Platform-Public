package edi.partners.dto;

import edi.partners.domain.MdnConfig;
import edi.partners.domain.MdnMode;
import edi.partners.domain.MdnReceiptType;
import java.time.Instant;
import java.util.UUID;

public record MdnConfigResponse(
        UUID id,
        UUID partnerId,
        String localFolder,
        MdnReceiptType receiptType,
        MdnMode deliveryMode,
        String asyncMdnUrl,
        String updatedBy,
        Instant updatedAt) {

    public static MdnConfigResponse of(MdnConfig c) {
        return new MdnConfigResponse(c.getId(), c.getPartner().getId(), c.getLocalFolder(), c.getReceiptType(),
                c.getDeliveryMode(), c.getAsyncMdnUrl(), c.getUpdatedBy(), c.getUpdatedAt());
    }
}
