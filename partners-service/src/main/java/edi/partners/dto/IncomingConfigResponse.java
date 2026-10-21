package edi.partners.dto;

import edi.partners.domain.IncomingConfig;
import java.time.Instant;
import java.util.UUID;

public record IncomingConfigResponse(
        UUID id,
        UUID partnerId,
        String incomingFolder,
        String parserProfile,
        String updatedBy,
        Instant updatedAt) {

    public static IncomingConfigResponse of(IncomingConfig c) {
        return new IncomingConfigResponse(c.getId(), c.getPartner().getId(), c.getIncomingFolder(),
                c.getParserProfile(), c.getUpdatedBy(), c.getUpdatedAt());
    }
}
