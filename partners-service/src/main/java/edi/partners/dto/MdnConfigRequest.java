package edi.partners.dto;

import edi.partners.domain.MdnMode;
import edi.partners.domain.MdnReceiptType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MdnConfigRequest(
        @NotBlank @Size(max = 512) String localFolder,
        @NotNull MdnReceiptType receiptType,
        @NotNull MdnMode deliveryMode,
        @Size(max = 1024) String asyncMdnUrl) {
}
