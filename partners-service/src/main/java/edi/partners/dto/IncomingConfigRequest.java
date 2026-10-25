package edi.partners.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncomingConfigRequest(
        @NotBlank @Size(max = 512) String incomingFolder,
        @Size(max = 128) String parserProfile) {
}
