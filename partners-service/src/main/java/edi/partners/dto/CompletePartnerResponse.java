package edi.partners.dto;

public record CompletePartnerResponse(
        PartnerResponse partner,
        OutgoingConfigResponse outgoing,
        IncomingConfigResponse incoming,
        MdnConfigResponse mdn) {
}
