package edi.partners.dto;

import jakarta.validation.constraints.NotNull;

public record PartnerStatusRequest(@NotNull Boolean active) {
}
